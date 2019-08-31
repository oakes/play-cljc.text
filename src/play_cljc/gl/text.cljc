(ns play-cljc.gl.text
  (:require [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.transforms :as t]
            [play-cljc.primitives-2d :as primitives]
            [play-cljc.math :as m]
            [play-cljc.gl.utils :as u]
            [play-cljc.instances :as i]
            #?(:clj  [play-cljc.macros-java :refer [gl]]
               :cljs [play-cljc.macros-js :refer-macros [gl]])))

(def ^:private ^:const reverse-matrix (m/scaling-matrix -1 -1))

(defn- project [entity width height]
  (update-in entity [:uniforms 'u_matrix]
    #(m/multiply-matrices 3 (m/projection-matrix width height) %)))

(defn- translate [entity x y]
  (update-in entity [:uniforms 'u_matrix]
    #(m/multiply-matrices 3 (m/translation-matrix x y) %)))

(defn- scale [entity x y]
  (update-in entity [:uniforms 'u_matrix]
    #(m/multiply-matrices 3 (m/scaling-matrix x y) %)))

(defn- rotate [entity angle]
  (update-in entity [:uniforms 'u_matrix]
    #(m/multiply-matrices 3 (m/rotation-matrix angle) %)))

(defn- camera [entity {:keys [matrix]}]
  (update-in entity [:uniforms 'u_matrix]
    #(->> %
          (m/multiply-matrices 3 matrix)
          (m/multiply-matrices 3 reverse-matrix))))

(def ^:private flip-y-matrix
  [1  0  0
   0 -1  0
   0  0  1])

;; InstancedFontEntity

(def ^:private instanced-font-vertex-shader
  {:inputs
   '{a_position vec2
     a_color vec4
     a_translate_matrix mat3
     a_texture_matrix mat3
     a_scale_matrix mat3}
   :uniforms
   '{u_matrix mat3}
   :outputs
   '{v_tex_coord vec2
     v_color vec4}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position
              (vec4
                (.xy (* u_matrix
                        a_translate_matrix
                        a_scale_matrix
                        (vec3 a_position 1)))
                0 1))
           (= v_tex_coord (.xy (* a_texture_matrix (vec3 a_position 1))))
           (= v_color a_color))}})

(def ^:private instanced-font-fragment-shader
  {:precision "mediump float"
   :uniforms
   '{u_image sampler2D}
   :inputs
   '{v_tex_coord vec2
     v_color vec4}
   :outputs
   '{o_color vec4}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (= o_color (texture u_image v_tex_coord))
           ("if" (== (.rgb o_color) (vec3 "0.0" "0.0" "0.0"))
             "discard")
           ("else"
             (= o_color v_color)))}})

(def ^:private instanced-font-attrs->unis
  '{a_translate_matrix u_translate_matrix
    a_scale_matrix u_scale_matrix
    a_texture_matrix u_texture_matrix
    a_color u_color})

(defrecord InstancedFontEntity [baked-font])

(extend-type InstancedFontEntity
  t/IProject
  (project [entity width height] (project entity width height))
  t/ITranslate
  (translate [entity x y] (translate entity x y))
  t/IScale
  (scale [entity x y] (scale entity x y))
  t/IRotate
  (rotate [entity angle] (rotate entity angle))
  t/ICamera
  (camera [entity cam] (camera entity cam))
  i/IInstanced
  (assoc [instanced-entity i entity]
    (reduce-kv
      (partial u/assoc-instance-attr i entity)
      instanced-entity
      instanced-font-attrs->unis))
  (dissoc [instanced-entity i]
    (reduce
      (partial u/dissoc-instance-attr i)
      instanced-entity
      (keys instanced-font-attrs->unis))))

;; FontEntity

(def ^:private font-vertex-shader
  {:inputs
   '{a_position vec2}
   :uniforms
   '{u_matrix mat3
     u_translate_matrix mat3
     u_scale_matrix mat3
     u_texture_matrix mat3}
   :outputs
   '{v_tex_coord vec2}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position
              (vec4
                (.xy (* u_matrix
                        u_translate_matrix
                        u_scale_matrix
                        (vec3 a_position 1)))
                0 1))
           (= v_tex_coord (.xy (* u_texture_matrix (vec3 a_position 1)))))}})

(def ^:private font-fragment-shader
  {:precision "mediump float"
   :uniforms
   '{u_image sampler2D
     u_color vec4}
   :inputs
   '{v_tex_coord vec2}
   :outputs
   '{o_color vec4}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (= o_color (texture u_image v_tex_coord))
           ("if" (== (.rgb o_color) (vec3 "0.0" "0.0" "0.0"))
             "discard")
           ("else"
             (= o_color u_color)))}})

(defrecord FontEntity [width height baked-font])

(extend-type FontEntity
  t/IProject
  (project [entity width height] (project entity width height))
  t/ITranslate
  (translate [entity x y] (translate entity x y))
  t/IScale
  (scale [entity x y] (scale entity x y))
  t/IRotate
  (rotate [entity angle] (rotate entity angle))
  t/ICamera
  (camera [entity cam] (camera entity cam))
  t/IColor
  (color [entity rgba]
    (assoc-in entity [:uniforms 'u_color] rgba))
  t/ICrop
  (crop [{:keys [width height] :as entity} crop-x crop-y crop-width crop-height]
    (update-in entity [:uniforms 'u_texture_matrix]
      #(->> %
            (m/multiply-matrices 3
              (m/translation-matrix (/ crop-x width) (/ crop-y height)))
            (m/multiply-matrices 3
              (m/scaling-matrix (/ crop-width width) (/ crop-height height))))))
  i/IInstance
  (->instanced-entity [entity]
    (-> entity
        (assoc :vertex instanced-font-vertex-shader
               :fragment instanced-font-fragment-shader)
        (update :uniforms dissoc
                'u_matrix 'u_texture_matrix 'u_color
                'u_scale_matrix 'u_translate_matrix)
        (update :uniforms merge {'u_matrix (m/identity-matrix 3)})
        (update :attributes merge {'a_translate_matrix {:data [] :divisor 1}
                                   'a_scale_matrix {:data [] :divisor 1}
                                   'a_texture_matrix {:data [] :divisor 1}
                                   'a_color {:data [] :divisor 1}})
        map->InstancedFontEntity)))

(defn ->font-entity
  "Returns an entity with all characters in the font. The second arity is for backwards
  compatibility and should not be used."
  ([game data baked-font]
   (-> (->font-entity game data (:bitmap-width baked-font) (:bitmap-height baked-font))
       (assoc :baked-font baked-font)
       map->FontEntity))
  ([game data width height]
   (-> (e/->image-entity game data width height)
       (assoc :vertex font-vertex-shader
              :fragment font-fragment-shader)
       (update :uniforms merge {'u_color [0 0 0 1]
                                'u_scale_matrix (m/identity-matrix 3)
                                'u_translate_matrix (m/identity-matrix 3)})
       #?(:clj (assoc-in [:uniforms 'u_image :opts]
                         {:mip-level 0
                          :internal-fmt (gl game RED)
                          :width width
                          :height height
                          :border 0
                          :src-fmt (gl game RED)
                          :src-type (gl game UNSIGNED_BYTE)})))))

(defn ->text-entity
  "Returns an entity with the given text rendered to it. The second arity is for backwards
  compatibility and should not be used."
 ([game
   {{:keys [baked-chars baseline
            font-height first-char
            bitmap-width bitmap-height]} :baked-font
    :as font-entity}
   text]
  (when-not (:program font-entity)
    (throw (ex-info "Only compiled font entities can be passed to ->text-entity" {})))
  (loop [text (seq text)
         total (float 0)
         inner-entities []]
    (if-let [ch (first text)]
      (let [{:keys [x y w h xoff yoff xadv]} (nth baked-chars (- #?(:clj (int ch) :cljs (.charCodeAt ch 0)) first-char))]
        (recur (rest text)
               (+ total (float xadv))
               (conj inner-entities
                     (-> font-entity
                         (t/project bitmap-width bitmap-height)
                         (t/crop x y w h)
                         (t/translate (+ xoff total) (- font-height baseline yoff))
                         (t/scale w h)
                         (update-in [:uniforms 'u_matrix]
                                    #(m/multiply-matrices 3 flip-y-matrix %))))))
      (-> (e/->image-entity game nil total font-height)
          (assoc
            :width total
            :height font-height
            :render-to-texture {'u_image (mapv #(assoc % :viewport {:x 0
                                                                    :y (- font-height bitmap-height)
                                                                    :width bitmap-width
                                                                    :height bitmap-height})
                                               inner-entities)})))))
 ([game baked-font image-entity text]
  (->text-entity game (assoc image-entity :baked-font baked-font) text)))

