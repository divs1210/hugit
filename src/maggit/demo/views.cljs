(ns maggit.demo.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [maggit.views :refer [navigable-list scrollable-list text-input]]))

(defn status []
  (let [{:keys [branch-name
                head-commit-message
                untracked
                unstaged
                staged]}
        @(rf/subscribe [:repo])

        {:keys [selected]}
        @(rf/subscribe [:status-view])]
    [:box#status
     {:top 0
      :right 0
      :width "100%"
      :style {:border {:fg :magenta}}
      :border {:type :line}
      :label " Status "}
     [:box#head
      {:top 1
       :left 1
       :right 2
       :align :left}
      [:text (str "Head: [" branch-name "] " head-commit-message)]]
     [navigable-list
      {:top 4
       :left 1
       :align :left
       :items [(str "Untracked (" (count untracked) ")")
               (str "Unstaged (" (count unstaged) ")")
               (str "Staged (" (count staged) ")")
               (str "Commit Log")]
       :selected selected
       :custom-key-handlers
       {["c"] {:f (fn [_]
                    (rf/dispatch [:assoc-in [:input-view]
                                  {:label "Commit Message"
                                   :on-submit (fn [msg]
                                                (rf/dispatch [:toast "Commiting"])
                                                (rf/dispatch [:commit msg])
                                                (rf/dispatch [:assoc-in [:status-view :selected] 3])
                                                (rf/dispatch [:assoc-in [:router/view] :commits]))
                                   :on-cancel #(rf/dispatch [:assoc-in [:router/view] :status])}])
                    (rf/dispatch [:assoc-in [:router/view] :input]))
               :label "Commit"
               :type "Action"}}
       :on-select
       (fn [x]
         (rf/dispatch [:assoc-in [:status-view :selected] x])
         (if (< x 3)
           (do
             (rf/dispatch [:assoc-in [:files-view]
                           (case x
                             0 {:label "Untracked"
                                :files-path [:repo :untracked]}
                             1 {:label "Unstaged"
                                :files-path [:repo :unstaged]}
                             2 {:label "Staged"
                                :files-path [:repo :staged]})])
             (rf/dispatch [:assoc-in [:router/view] :files]))
           (rf/dispatch [:assoc-in [:router/view] :commits])))}]]))

(defn files []
  (let [{:keys [files-path label selected]}
        @(rf/subscribe [:files-view])

        files (rf/subscribe [:get-in files-path])]
    [:box#files
     {:top 0
      :right 0
      :width "100%"
      :style {:border {:fg :magenta}}
      :border {:type :line}
      :label (str " " label " ")}
     [navigable-list
      {:top 1
       :left 1
       :right 2
       :align :left
       :items @files
       :custom-key-handlers
       {["s"] {:f (fn [x]
                    (rf/dispatch [:toast "Staging " (nth @files x)])
                    (rf/dispatch [:stage-file (nth @files x)]))
               :label "Stage"
               :type "Action"}
        ["u"] {:f (fn [x]
                    (rf/dispatch [:toast "Unstaging " (nth @files x)])
                    (rf/dispatch [:unstage-file (nth @files x)]))
               :label "Unstage"
               :type "Action"}
        ["r"] {:f (fn [x]
                    (rf/dispatch [:toast "Untracking " (nth @files x)])
                    (rf/dispatch [:untrack-file (nth @files x)]))
               :label "Untrack"
               :type "Action"}
        ["k"] {:f (fn [x]
                    (rf/dispatch [:toast "Checking out " (nth @files x)])
                    (rf/dispatch [:checkout-file (nth @files x)]))
               :label "Checkout"
               :type "Action"}}
       :on-select
       (fn [x]
         (rf/dispatch [:assoc-in [:diffs-view] {:file-path (nth @files x)}])
         (rf/dispatch [:assoc-in [:router/view] :diffs]))
       :on-back
       #(do
          (rf/dispatch [:assoc-in [:files-view] {}])
          (rf/dispatch [:assoc-in [:router/view] :status]))}]]))

(defn diffs []
  (let [{:keys [file-path]} @(rf/subscribe [:diffs-view])
        {:keys [old new]} @(rf/subscribe [:get-in [:repo :unstaged-diffs file-path]])
        size @(rf/subscribe [:size])
        rows (r/atom (:rows size))]
    [:box#commits
     {:top 0
      :right 0
      :width "100%"
      :style {:border {:fg :magenta}}
      :border {:type :line}
      :label (str " ( " file-path " ) ")}
     [scrollable-list
      {:top 1
       :left 1
       :right 2
       :width "50%"
       :align :left
       :window-size (-> @rows (* 0.6) (- 4))
       :items (clojure.string/split old #"\n")
       :on-back
       #(rf/dispatch [:assoc-in [:router/view] :files])}]
     [scrollable-list
      {:top 1
       :left "50%" 
       :right 2
       :width "50%"
       :align :left
       :window-size (-> @rows (* 0.6) (- 4))
       :items (clojure.string/split new #"\n")
       :on-back
       #(rf/dispatch [:assoc-in [:router/view] :files])}]]))


(defn commits []
  (let [commits @(rf/subscribe [:get-in [:repo :commits]])
        size @(rf/subscribe [:size])
        rows (r/atom (:rows size))]
    (with-meta
      [:box#commits
       {:top 0
        :right 0
        :width "100%"
        :style {:border {:fg :magenta}}
        :border {:type :line}
        :label " Commit Log "}
       [scrollable-list
        {:top 1
         :left 1
         :right 2
         :align :left
         :window-size (-> @rows (* 0.6) (- 4))
         :items (for [{:keys [sha summary]} commits]
                  (str (->> sha (take 7) clojure.string/join)
                       " "
                       summary))
         :on-back
         #(rf/dispatch [:assoc-in [:router/view] :status])}]]
      {:component-did-mount
       (fn [this]
         (reset! rows (-> this .-refs .-commits .-height)))})))

(defn input []
  (let [{:keys [label on-submit on-cancel]}
        @(rf/subscribe [:input-view])]
    [:box
     {:top 0
      :right 0
      :width "100%"
      :style {:border {:fg :magenta}}
      :border {:type :line}
      :label (str " " label " ")}
     [text-input
      {:top 1
       :left 1
       :height 10
       :on-submit on-submit
       :on-cancel on-cancel}]]))

(defn viewport [height]
  [:box#viewport
   {:height height}
   (let [view @(rf/subscribe [:view])]
     [(case view
        :status status
        :files files
        :commits commits
        :diffs diffs
        :input input)])])

(defn toast []
  (let [text @(rf/subscribe [:get-in [:toast-view :text]])]
    [:box#toast
     {:bottom 0
      :height 3
      :style {:border {:fg :magenta}}
      :border {:type :line}}
     text]))

(defn home []
  (let [size @(rf/subscribe [:size])
        rows (:rows size)]
    [:box#home
     {:top 0
      :left 0
      :height "100%"
      :width "100%"}
     [viewport (- rows 3)]
     [toast]]))
