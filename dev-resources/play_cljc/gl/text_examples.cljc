(ns play-cljc.gl.text-examples
  (:require [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.gl.example-utils :as eu]
            [play-cljc.transforms :as t]
            [play-cljc.gl.text :as text]
            #?(:clj  [play-cljc.macros-java :refer [gl]]
               :cljs [play-cljc.macros-js :refer-macros [gl]])
            #?(:clj [dynadoc.example :refer [defexample]])
            #?(:clj [play-cljc.gl.example-fonts :refer [load-bitmap-clj ->text-entity-clj]]))
  #?(:cljs (:require-macros [dynadoc.example :refer [defexample]]
                            [play-cljc.gl.example-fonts :refer [load-bitmap-cljs ->text-entity-cljs]])))

(defn load-roboto [callback]
  (#?(:clj load-bitmap-clj :cljs load-bitmap-cljs) :roboto callback))

;; ->font-entity

(defn font-entity-example [game image font-entity]
  (gl game disable (gl game CULL_FACE))
  (gl game disable (gl game DEPTH_TEST))
  (assoc game
    :entity
    (-> (play-cljc.gl.core/compile game font-entity)
        (assoc :clear {:color [1 1 1 1] :depth 1}))
    :image
    image))

(defexample play-cljc.gl.text/->font-entity
  {:with-card card
   :with-focus [focus (play-cljc.gl.text/->font-entity game data width height)]}
  (let [game (play-cljc.gl.example-utils/init-example card)]
    (play-cljc.gl.text-examples/load-roboto
      (fn [{:keys [data width height] :as image}]
        (->> focus
             (play-cljc.gl.text-examples/font-entity-example game image)
             (play-cljc.gl.example-utils/game-loop
               (fn font-entity-render [{:keys [entity image] :as game}]
                 (play-cljc.gl.example-utils/resize-example game)
                 (let [game-width (play-cljc.gl.example-utils/get-width game)
                       game-height (play-cljc.gl.example-utils/get-height game)
                       screen-ratio (/ game-width game-height)
                       image-ratio (/ (:width image) (:height image))
                       [img-width img-height] (if (> screen-ratio image-ratio)
                                                [(* game-height (/ (:width image) (:height image))) game-height]
                                                [game-width (* game-width (/ (:height image) (:width image)))])]
                   (play-cljc.gl.core/render game
                     (-> entity
                         (assoc :viewport {:x 0 :y 0 :width game-width :height game-height})
                         (play-cljc.transforms/project game-width game-height)
                         (play-cljc.transforms/translate 0 0)
                         (play-cljc.transforms/scale img-width img-height))))
                 game)))))))

