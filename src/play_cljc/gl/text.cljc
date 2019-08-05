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

(defrecord InstancedFontEntity [baked-font characters])

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
    (reduce-kv
      (partial u/dissoc-instance-attr i)
      instanced-entity
      instanced-font-attrs->unis)))

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
               :fragment instanced-font-fragment-shader
               :characters [])
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

;; CharEntity

(defrecord CharEntity [baked-char])

(extend-type CharEntity
  t/IColor
  (color [entity rgba]
    (assoc-in entity [:uniforms 'u_color] rgba)))

(defn ->char-entity [{:keys [baked-font] :as font-entity} ch]
  (let [{:keys [baked-chars baseline first-char]} baked-font
        char-code (- #?(:clj (int ch) :cljs (.charCodeAt ch 0)) first-char)
        baked-char (nth baked-chars char-code)
        {:keys [x y w h xoff yoff]} baked-char]
    (-> font-entity
        (t/crop x y w h)
        (assoc-in [:uniforms 'u_scale_matrix]
                  (m/scaling-matrix w h))
        (assoc-in [:uniforms 'u_translate_matrix]
                  (m/translation-matrix xoff (+ baseline yoff)))
        (assoc :baked-char baked-char)
        map->CharEntity)))

(defn assoc-char
  ([text-entity index char-entity]
   (assoc-char text-entity 0 index char-entity))
  ([{:keys [baked-font characters] :as text-entity} line-num index {:keys [baked-char] :as char-entity}]
   (let [line (or (get characters line-num) [])
         prev-chars (subvec line 0 index)
         prev-xadv (reduce + 0 (map #(-> % :baked-char :xadv) prev-chars))
         x-total (+ (:xadv baked-char) prev-xadv)
         y-total (* line-num (:font-height baked-font))
         prev-lines (subvec characters 0 line-num)
         prev-count (reduce + 0 (map count prev-lines))
         replaced-char (get line index)
         line (assoc line index (assoc char-entity :x-total x-total))
         next-char (get line (inc index))]
     (-> text-entity
         (assoc-in [:characters line-num] line)
         (i/assoc (+ index prev-count)
                  (-> char-entity
                      (update-in [:uniforms 'u_translate_matrix]
                        #(m/multiply-matrices 3 (m/translation-matrix prev-xadv y-total) %))))
         ;; adjust the next char if its horizontal position changed
         (cond-> (and next-char (not= (:x-total replaced-char) x-total))
                 (assoc-char line-num (inc index) next-char))))))

(defn dissoc-char
  ([text-entity index]
   (dissoc-char text-entity 0 index))
  ([{:keys [characters] :as text-entity} line-num index]
   (let [line (nth characters line-num)
         prev-lines (subvec characters 0 line-num)
         prev-count (reduce + 0 (map count prev-lines))
         v1 (subvec line 0 index)
         v2 (subvec line (inc index))
         line (into (into [] v1) v2)
         next-char (get line index)]
     (-> text-entity
         (assoc-in [:characters line-num] line)
         (i/dissoc (+ index prev-count))
         (cond-> next-char
                 (assoc-char line-num index next-char))))))

(defn ->text-entity
 ([game
   {{:keys [baked-chars baseline
            font-height first-char
            bitmap-width bitmap-height]} :baked-font
    :as font-entity}
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
                                               inner-entities)})))))
 ([game baked-font image-entity text]
  (->text-entity game (assoc image-entity :baked-font baked-font) text)))

