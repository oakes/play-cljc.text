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

(def ^:private flip-y-matrix
  [1  0  0
   0 -1  0
   0  0  1])

(def ^:private instanced-font-vertex-shader
  {:inputs
   '{a_position vec2
     a_translate_matrix mat3
     a_texture_matrix mat3
     a_scale_matrix mat3}
   :uniforms
   '{u_matrix mat3}
   :outputs
   '{v_tex_coord vec2}
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
           (= v_tex_coord (.xy (* a_texture_matrix (vec3 a_position 1)))))}})

(def ^:private instanced-font-fragment-shader
  {:precision "mediump float"
   :uniforms
   '{u_image sampler2D}
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
             (= o_color (vec4 "0.0" "0.0" "0.0" "1.0"))))}})

(def ^:private instanced-font-attrs->unis
  '{a_translate_matrix u_translate_matrix
    a_scale_matrix u_scale_matrix
    a_texture_matrix u_texture_matrix})

(defrecord InstancedFontEntity [font-entity baked-font])

(extend-type InstancedFontEntity
  t/IProject
  (project [entity width height]
    (update-in entity [:uniforms 'u_matrix]
      #(m/multiply-matrices 3 (m/projection-matrix width height) %)))
  t/IScale
  (scale [entity x y]
    (update-in entity [:uniforms 'u_matrix]
      #(m/multiply-matrices 3 (m/scaling-matrix x y) %)))
  t/ICamera
  (camera [entity {:keys [matrix]}]
    (update-in entity [:uniforms 'u_matrix]
      #(->> %
            (m/multiply-matrices 3 matrix)
            (m/multiply-matrices 3 reverse-matrix))))
  i/IInstanced
  (assoc [instanced-entity i entity]
    (reduce-kv
      (partial u/assoc-instance-attr i entity)
      instanced-entity
      instanced-font-attrs->unis))
  (dissoc [instanced-entity i]
    (reduce-kv
      (partial u/dissoc-instance-attr i)
      instanced-entity
      instanced-font-attrs->unis)))

(def ^:private font-vertex-shader
  {:inputs
   '{a_position vec2}
   :uniforms
   '{u_matrix mat3
     u_texture_matrix mat3}
   :outputs
   '{v_tex_coord vec2}
   :signatures
   '{main ([] void)}
   :functions
   '{main ([]
           (= gl_Position
              (vec4
                (.xy (* u_matrix (vec3 a_position 1)))
                0 1))
           (= v_tex_coord (.xy (* u_texture_matrix (vec3 a_position 1)))))}})

(def ^:private font-fragment-shader
  {:precision "mediump float"
   :uniforms
   '{u_image sampler2D}
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
             (= o_color (vec4 "0.0" "0.0" "0.0" "1.0"))))}})

(defn ->font-entity [game data width height]
  (-> (e/->image-entity game data width height)
      (assoc :vertex font-vertex-shader
             :fragment font-fragment-shader)
      #?(:clj (assoc-in [:uniforms 'u_image :opts]
                        {:mip-level 0
                         :internal-fmt (gl game RED)
                         :width width
                         :height height
                         :border 0
                         :src-fmt (gl game RED)
                         :src-type (gl game UNSIGNED_BYTE)}))))

(defn assoc-text [{:keys [font-entity baked-font] :as text-entity} text]
  (let [{:keys [baked-chars baseline
                font-height first-char
                bitmap-width bitmap-height]} baked-font]
    (loop [text (seq text)
           total 0
           entity text-entity
           index 0]
      (if-let [ch (first text)]
        (let [{:keys [x y w h xoff yoff xadv]} (nth baked-chars (- #?(:clj (int ch) :cljs (.charCodeAt ch 0)) first-char))]
          (recur (rest text)
                 (+ total xadv)
                 (i/assoc entity index
                          (-> font-entity
                              (t/crop x y w h)
                              (assoc-in [:uniforms 'u_scale_matrix]
                                        (m/scaling-matrix w h))
                              (assoc-in [:uniforms 'u_translate_matrix]
                                        (m/translation-matrix (+ xoff total) (+ baseline yoff)))))
                 (inc index)))
        entity))))

(defn ->text-entity
  ([game baked-font font-entity]
   (-> font-entity
       (assoc :vertex instanced-font-vertex-shader
              :fragment instanced-font-fragment-shader
              :baked-font baked-font
              :font-entity font-entity)
       (update :uniforms dissoc 'u_matrix 'u_texture_matrix)
       (update :uniforms merge {'u_matrix (m/identity-matrix 3)})
       (update :attributes merge {'a_translate_matrix {:data [] :divisor 1}
                                  'a_scale_matrix {:data [] :divisor 1}
                                  'a_texture_matrix {:data [] :divisor 1}})
       map->InstancedFontEntity))
  ([game
    {:keys [baked-chars baseline
            font-height first-char
            bitmap-width bitmap-height]
     :as baked-font}
    font-entity
    text]
   (loop [text (seq text)
          total 0
          inner-entities []]
     (if-let [ch (first text)]
       (let [{:keys [x y w h xoff yoff xadv]} (nth baked-chars (- #?(:clj (int ch) :cljs (.charCodeAt ch 0)) first-char))]
         (recur (rest text)
                (+ total xadv)
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
                                                inner-entities)}))))))

