(ns hugit.app.views
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [hugit.util :as u]
            [hugit.keys :refer [with-keys]]
            [hugit.views :refer [navigable-list scrollable-list text-input]]))

(defn <sub [query]
  (rf/subscribe [:get-in query]))

(defn toast> [& msgs]
  (rf/dispatch-sync (vec (cons :toast msgs))))

(defn status []
  (let [{:keys [branch-name
                head-commit-summary
                untracked
                unstaged
                staged]}
        @(<sub [:repo])

        selected (<sub [:router/view-state :selected])]
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
      [:text (str "Head: [" branch-name "] " head-commit-summary)]]
     [navigable-list
      {:top 4
       :left 1
       :align :left
       :items [(str "Untracked (" (count untracked) ")")
               (str "Unstaged (" (count unstaged) ")")
               (str "Staged (" (count staged) ")")
               (str "Commit Log")]
       :selected @selected
       :custom-key-handlers
       {["c"] {:f (fn [idx]
                    (rf/dispatch [:router/goto :input
                                  {:label "Commit Message"
                                   :on-submit (fn [msg]
                                                (toast> "Commiting")
                                                (rf/dispatch [:commit msg])
                                                (rf/dispatch [:router/goto-and-forget :commits]))
                                   :on-cancel #(rf/dispatch [:router/go-back])}]))
               :label "Commit"
               :type "Action"}
        ["p"] {:f (fn [_]
                    (toast> "Pushing")
                    (rf/dispatch [:push]))
               :label "Push"
               :type "Action"}}
       :on-select
       (fn [x]
         (rf/dispatch [:assoc-in [:router/view-state :selected] x])
         (cond
           (< x 3)
           (rf/dispatch [:router/goto :files
                         (case x
                             0 {:label "Untracked"
                                :files-path [:repo :untracked]
                                :on-select
                                (fn [file-idx]
                                  (let [file (nth @(<sub [:repo :untracked])
                                                  file-idx)]
                                    (rf/dispatch
                                     [:router/goto :file
                                      {:label file
                                       :content-path [:repo :untracked-content file]}])))}
                             1 {:label "Unstaged"
                                :files-path [:repo :unstaged]
                                :on-select
                                (fn [file-idx]
                                  (let [file (nth @(<sub [:repo :unstaged])
                                                  file-idx)]
                                    (rf/dispatch
                                     [:router/goto :diffs
                                      {:label file
                                       :file file
                                       :hunks-path [:repo :unstaged-hunks file]}])))}
                             2 {:label "Staged"
                                :files-path [:repo :staged]
                                :on-select
                                (fn [file-idx]
                                  (let [file (nth @(<sub [:repo :staged])
                                                  file-idx)]
                                    (rf/dispatch
                                     [:router/goto :diffs
                                      {:label file
                                       :file file
                                       :hunks-path [:repo :staged-hunks file]}])))})])
           (== x 3)
           (rf/dispatch [:router/goto :commits])))}]]))

(defn files []
  (let [{:keys [files-path label selected on-select]}
        @(<sub [:router/view-state])

        files (<sub files-path)]
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
       :selected selected
       :custom-key-handlers
       {["s"] {:f (fn [x]
                    (toast> "Staging " (nth @files x))
                    (rf/dispatch [:stage-file (nth @files x)]))
               :label "Stage"
               :type "Action"}
        ["u"] {:f (fn [x]
                    (toast> "Unstaging " (nth @files x))
                    (rf/dispatch [:unstage-file (nth @files x)]))
               :label "Unstage"
               :type "Action"}
        ["r"] {:f (fn [x]
                    (toast> "Untracking " (nth @files x))
                    (rf/dispatch [:untrack-file (nth @files x)]))
               :label "Untrack"
               :type "Action"}
        ["k"] {:f (fn [x]
                    (toast> "Checking out " (nth @files x))
                    (rf/dispatch [:checkout-file (nth @files x)]))
               :label "Checkout"
               :type "Action"}}
       :on-select
       (fn [idx]
         (rf/dispatch [:assoc-in [:router/view-state :selected] idx])
         (if (some? on-select)
           (on-select idx)
           (rf/dispatch [:show-file (nth @files idx)])))
       :on-back
       #(rf/dispatch [:router/go-back])}]]))

(defn file []
  (let [label (<sub [:router/view-state :label])
        content-path (<sub [:router/view-state :content-path])
        text (<sub @content-path)
        size (<sub [:terminal/size])
        rows (:rows @size)]
    [:box#diffs
     {:top 0
      :right 0
      :width "100%"
      :style {:border {:fg :magenta}}
      :border {:type :line}
      :label (str " " @label " ")}
     [scrollable-list
      {:top 0
       :left 1
       :right 2
       :align :left
       :window-size (- rows 6)
       :items (clojure.string/split @text #"\n")
       :on-back
       #(rf/dispatch [:router/go-back])}]]))

(defn diffs []
  (let [label (<sub [:router/view-state :label])
        file  (<sub [:router/view-state :file])
        hunks-path (<sub [:router/view-state :hunks-path])
        hunks (<sub @hunks-path)
        separated-hunks (fn [hunks]
                          (interleave hunks
                                      (repeat (count hunks)
                                              {:dummy? true
                                               :size 1
                                               :text "====="})))
        size (<sub [:terminal/size])
        rows (:rows @size)]
    [:box#diffs
     {:top 0
      :right 0
      :width "100%"
      :style {:border {:fg :magenta}}
      :border {:type :line}
      :label (str " "
                  (or @label
                      "Diff")
                  " ")}
     [scrollable-list
      {:top 0
       :left 1
       :right 2
       :align :left
       :window-size (- rows 6)
       :items (for [{:keys [text]} (separated-hunks @hunks)
                    :let [lines (clojure.string/split text #"\n")]
                    line lines]
                line)
       :item-props-f
       (fn [line]
         (case (first line)
           \- {:style {:fg :red}}
           \+ {:style {:fg :green}}
           {:style {:fg :white}}))
       :custom-key-handlers
       {["s"] {:f (fn [idx]
                    (let [hunk (u/nth-weighted-item
                                (separated-hunks @hunks)
                                :size
                                idx)]
                      (when-not (:dummy? hunk)
                        (toast> "Staging hunk")
                        (rf/dispatch [:stage-hunk hunk]))))
               :label "Stage Hunk"
               :type "Action"}
        ["u"] {:f (fn [idx]
                    (let [hunk (u/nth-weighted-item
                                (separated-hunks @hunks)
                                :size
                                idx)]
                      (when-not (:dummy? hunk)
                        (toast> "Unstaging hunk")
                        (rf/dispatch [:unstage-hunk hunk]))))
               :label "Unstage Hunk"
               :type "Action"}
        ["k"] {:f (fn [idx]
                    (let [hunk (u/nth-weighted-item
                                (separated-hunks @hunks)
                                :size
                                idx)]
                      (when-not (:dummy? hunk)
                        (toast> "NOT IMPLEMENTED: Discarding hunk")
                        (rf/dispatch [:discard-hunk hunk]))))
               :label "Discard Hunk"
               :type "Action"}}
       :on-back
       #(rf/dispatch [:router/go-back])}]]))

(defn commits []
  (let [commits (<sub [:repo :commits])
        selected (<sub [:router/view-state :selected])
        size (<sub [:terminal/size])
        rows (:rows @size)
        commit-str (fn [{:keys [sha summary]}]
                     (str (->> sha (take 7) clojure.string/join)
                          " "
                          summary))]
    [:box#commits
     {:top 0
      :right 0
      :width "100%"
      :style {:border {:fg :magenta}}
      :border {:type :line}
      :label " Commit Log "}
     [scrollable-list
      {:top 0
       :left 1
       :right 2
       :align :left
       :window-size (- rows 6)
       :items (map commit-str @commits)
       :selected @selected
       :on-select
       (fn [idx]
         (let [commit (nth @commits idx)]
           (rf/dispatch [:assoc-in [:router/view-state :selected] idx])
           (rf/dispatch [:get-commit-hunks commit])
           (rf/dispatch
            [:router/goto :diffs
             {:label (commit-str commit)
              :hunks-path [:repo :commit-hunks]}])))
       :on-back
       #(rf/dispatch [:router/go-back])}]]))

(defn input []
  (let [{:keys [label on-submit on-cancel]}
        @(<sub [:router/view-state])]
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
   (let [view @(<sub [:router/view])]
     [(case view
        :status status
        :files files
        :file file
        :commits commits
        :diffs diffs
        :input input)])])

(defn toast []
  (let [text @(<sub [:toast/view-state :text])]
    [:box#toast
     {:bottom 0
      :height 3
      :style {:border {:fg :magenta}}
      :border {:type :line}}
     [:text
      {:left 1}
      text]]))

(defn home [screen]
  (let [size (<sub [:terminal/size])
        rows (:rows @size)]
    (with-keys @screen
      {["l"] {:f #(rf/dispatch [:logs/show-logs])
              :label "View Logs"
              :type "Action"}}
      [:box#home
       {:top 0
        :left 0
        :height "100%"
        :width "100%"}
       [viewport (- rows 3)]
       [toast]])))