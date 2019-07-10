(ns play-cljc.gl.example-utils
  (:require [play-cljc.gl.core :as c]
            [play-cljc.math :as m]
            #?(:clj  [clojure.java.io :as io]
               :cljs [goog.events :as events])
            #?(:clj  [play-cljc.macros-java :refer [math]]
               :cljs [play-cljc.macros-js :refer-macros [math]]))
  #?(:clj (:import [org.lwjgl.glfw GLFW]
                   [org.lwjgl.system MemoryUtil])))

(def PI (math PI))
(def cos #(math cos %))
(def sin #(math sin %))

(def textures (atom 0))

(defn init-example [#?(:clj window :cljs card)]
  #?(:clj  (assoc (c/->game window)
                  :tex-count textures
                  :total-time 0
                  :delta-time 0)
     :cljs (do
             (when-let [canvas (.querySelector card "canvas")]
               (.removeChild card canvas))
             (let [canvas (doto (js/document.createElement "canvas")
                            (-> .-style .-width (set! "100%"))
                            (-> .-style .-height (set! "100%")))
                   context (.getContext canvas "webgl2")]
               (.appendChild card canvas)
               (assoc (c/->game context)
                      :total-time 0
                      :delta-time 0)))))

(defn game-loop [f game]
  #?(:clj  {:f f :game game}
     :cljs (let [game (f game)]
             (js/requestAnimationFrame
               (fn [ts]
                 (let [ts (* ts 0.001)]
                   (game-loop f (assoc game
                                       :delta-time (- ts (:total-time game))
                                       :total-time ts))))))))

(defn resize-example [{:keys [context] :as game}]
  #?(:cljs (let [display-width context.canvas.clientWidth
                 display-height context.canvas.clientHeight]
             (when (or (not= context.canvas.width display-width)
                       (not= context.canvas.height display-height))
               (set! context.canvas.width display-width)
               (set! context.canvas.height display-height)))))

(defn get-width [game]
  #?(:clj  (let [*width (MemoryUtil/memAllocInt 1)
                 *height (MemoryUtil/memAllocInt 1)
                 _ (GLFW/glfwGetFramebufferSize (:context game) *width *height)
                 n (.get *width)]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             n)
     :cljs (-> game :context .-canvas .-clientWidth)))

(defn get-height [game]
  #?(:clj  (let [*width (MemoryUtil/memAllocInt 1)
                 *height (MemoryUtil/memAllocInt 1)
                 _ (GLFW/glfwGetFramebufferSize (:context game) *width *height)
                 n (.get *height)]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             n)
     :cljs (-> game :context .-canvas .-clientHeight)))

