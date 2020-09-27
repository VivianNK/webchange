(ns webchange.editor-v2.wizard.views
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [cljs-react-material-ui.icons :as ic]
    [re-frame.core :as re-frame]
    [reagent.core :as r]
    [webchange.editor-v2.layout.views :refer [layout]]
    [webchange.editor-v2.wizard.state.course :as state-course]
    [webchange.editor-v2.wizard.state.activity :as state-activity]
    [webchange.editor-v2.components.file-input.views :as file-input]
    [webchange.editor-v2.concepts.events :as concepts-events]))

(defn- course-info
  []
  (r/with-let [data (r/atom {})]
    (let [datasets @(re-frame/subscribe [::state-course/datasets-library])]
      [ui/card {:style {:margin      "12px"
                        :flex-shrink "0"}}
       [ui/card-content
        [ui/grid {:container   true
                  :justify     "space-between"
                  :spacing     24
                  :align-items "center"}
         [ui/grid {:item true :xs 7}
          [ui/text-field {:label         "Name"
                          :full-width    true
                          :variant       "outlined"
                          :default-value (:name @data)
                          :on-change     #(swap! data assoc :name (-> % .-target .-value))}]]
         [ui/grid {:item true :xs 7}
          [ui/text-field {:label         "Language"
                          :full-width    true
                          :variant       "outlined"
                          :default-value (:lang @data)
                          :on-change     #(swap! data assoc :lang (-> % .-target .-value))}]]
         [ui/grid {:item true :xs 7}
          [ui/input-label "Concept list"]
          [ui/select {:value     (:concept-list-id @data)
                      :on-change #(swap! data assoc :concept-list-id (-> % .-target .-value))
                      :style     {:min-width "150px"}}
           (for [dataset datasets]
             ^{:key (:id dataset)}
             [ui/menu-item {:value (:id dataset)} (:name dataset)])]]]]
       [ui/card-actions
        [ui/button {:color    "secondary"
                    :style    {:margin-left "auto"}
                    :on-click #(re-frame/dispatch [::state-course/create-course @data])}
         "Save"]]])))

(defn create-course-panel
  []
  (re-frame/dispatch [::state-course/load-datasets-library])
  [layout {:title "Course"}
   [:div {:style {:height         "100%"
                  :display        "flex"
                  :flex-direction "column"}}
    [course-info]]])

(def animation-names ["senoravaca" "vera" "mari"])

(defn- string-option
  [key option data]
  [ui/grid {:item true :xs 7}
   [ui/text-field {:label  (:label option)
                   :variant   "outlined"
                   :value     (get @data key)
                   :on-change #(swap! data assoc key (-> % .-target .-value))}]])

(defn- lookup-option
  [key option data]
  [ui/grid {:item true :xs 7}
   [ui/input-label (:label option)]
   [ui/select {:value     (get @data key)
               :on-change #(swap! data assoc key (-> % .-target .-value))
               :style     {:min-width "150px"}}
    (for [{:keys [name value]} (:options option)]
      ^{:key value}
      [ui/menu-item {:value value} name])]])

(defn- characters-option
  [key option data]
  (let [values (get @data key)]
    (when (nil? values)
      (swap! data assoc key []))
    [ui/grid {:item true :xs 7}
     [ui/input-label (:label option)]
     (for [idx (range (:max option))]
       (let [character (get values idx {})]
         ^{:key idx}
         [ui/grid {:container true}
          [ui/grid {:item true}
           [ui/text-field {:label     "Name"
                           :variant   "outlined"
                           :value     (:name character)
                           :on-change #(swap! data assoc-in [key idx :name] (-> % .-target .-value))}]]
          [ui/grid {:item true}
           [ui/select {:value     (:skeleton character)
                       :on-change #(swap! data assoc-in [key idx :skeleton] (-> % .-target .-value))
                       :style     {:min-width "150px"}}
            (for [animation-name animation-names]
              ^{:key animation-name}
              [ui/menu-item {:value animation-name} animation-name])]]]))]))

(defn- select-file-form
  [type uploading-atom on-change]
  (let [on-finish (fn [result]
                    (on-change (:url result))
                    (reset! uploading-atom false))
        on-change (fn [js-file]
                    (reset! uploading-atom true)
                    (re-frame/dispatch [::concepts-events/upload-asset js-file {:type type :on-finish on-finish}]))]
    [file-input/select-file-form {:on-change on-change}]))

(defn- image-field
  [value on-change]
  (r/with-let [uploading (r/atom false)]
    [ui/grid {:container true :justify "flex-start" :align-items "flex-end"}
     (if value
       [ui/avatar {:style {:width 60 :height 60} :src value}]
       [ui/avatar {:style {:width 60 :height 60}} [ic/image]])
     [ui/text-field {:style {:width "50%"} :value (str value) :on-change #(on-change (-> % .-target .-value))}]
     [select-file-form :image uploading on-change]
     (when @uploading
       [ui/circular-progress])]))

(defn- pages-option
  [key option data]
  (let [values (get @data key)]
    (when (nil? values)
      (swap! data assoc key []))
    [ui/grid {:item true :xs 12}
     [ui/input-label (:label option)]
     (for [idx (range (:max option))]
       (let [page (get values idx {})]
         ^{:key idx}
         [ui/grid {:container true}
          [ui/grid {:item true}
           [ui/text-field {:label     "Text"
                           :variant   "outlined"
                           :value     (:name page)
                           :on-change #(swap! data assoc-in [key idx :text] (-> % .-target .-value))
                           :style {:min-width "500px"}}]]
          [ui/grid {:item true}
           [image-field (:img page) #(swap! data assoc-in [key idx :img] %)]]]))]))

(defn- option-info
  [key option data]
  (case (:type option)
    "characters" [characters-option key option data]
    "lookup" [lookup-option key option data]
    "string" [string-option key option data]
    "pages" [pages-option key option data]
    nil))

(defn- template-info
  [template data]
  [ui/grid {:container   true
            :justify     "space-between"
            :spacing     24
            :align-items "center"}
   (for [[key option] (:options template)]
     [option-info key option data])])

(defn- render-selected
  [selected]
  (let [skills (->> @(re-frame/subscribe [::state-activity/skills])
                    :skills
                    (map (juxt :id identity))
                    (into {}))
        values (->> selected
                    vals
                    (map #(get skills %)))]
    [ui/list
     (for [value values]
       [ui/list-item {:key (:id value)}
        [ui/list-item-text {:primary (:name value)}]])]))

(def react-render-selected (r/reactify-component render-selected))

(defn- activity-skill-info
  [data]
  (let [skills (-> @(re-frame/subscribe [::state-activity/skills]) :skills)]
    [ui/grid {:item true :xs 7}
     [ui/input-label "Skills"]
     [ui/select {:multiple true
                 :render-value #(r/create-element react-render-selected %)
                 :value     (:skills @data)
                 :on-change #(swap! data assoc :skills (->> % .-target .-value))
                 :style     {:min-width "150px"}}
      (for [skill skills]
        ^{:key (:id skill)}
        [ui/menu-item {:value (:id skill)} (:name skill)])]]))

(defn- activity-info
  [course-slug]
  (r/with-let [data (r/atom {:skills []})]
    (let [templates @(re-frame/subscribe [::state-activity/templates])
          current-template (->> templates (filter #(= (:template-id @data) (:id %))) first)]
      [ui/card {:style {:margin      "12px"
                        :flex-shrink "0"}}
       [ui/card-content
        [ui/grid {:container   true
                  :justify     "space-between"
                  :spacing     24
                  :align-items "center"}
         [ui/grid {:item true :xs 7}
          [ui/text-field {:label         "Name"
                          :full-width    true
                          :variant       "outlined"
                          :default-value (:name @data)
                          :on-change     #(swap! data assoc :name (-> % .-target .-value))}]]
         [activity-skill-info data]
         [ui/grid {:item true :xs 7}
          [ui/input-label "Template"]
          [ui/select {:value     (or (:template-id @data) "")
                      :on-change #(swap! data assoc :template-id (-> % .-target .-value))
                      :style     {:min-width "150px"}}
           (for [template templates]
             ^{:key (:id template)}
             [ui/menu-item {:value (:id template)} (:name template)])]]
         [template-info current-template data]]]
       [ui/card-actions
        [ui/button {:color    "secondary"
                    :style    {:margin-left "auto"}
                    :on-click #(re-frame/dispatch [::state-activity/create-activity course-slug @data])}
         "Save"]]])))

(defn create-activity-panel
  [course-slug]
  (re-frame/dispatch [::state-activity/load-templates])
  (re-frame/dispatch [::state-activity/load-skills])
  [layout {:title "Activity"}
   [:div {:style {:height         "100%"
                  :display        "flex"
                  :flex-direction "column"}}
    [activity-info course-slug]]])

(defn- simple-activity-info
  []
  (r/with-let [default-skill 4
               default-template 5
               data (r/atom {:lang "english" :skills [default-skill] :name "Activity" :template-id default-template})]
    (let [templates @(re-frame/subscribe [::state-activity/templates])
          current-template (->> templates (filter #(= (:template-id @data) (:id %))) first)]
      [ui/card {:style {:margin      "12px"
                        :flex-shrink "0"}}
       [ui/card-content
        [ui/grid {:container   true
                  :justify     "space-between"
                  :spacing     24
                  :align-items "center"}
         [ui/grid {:item true :xs 7}
          [ui/text-field {:label         "Name"
                          :full-width    true
                          :variant       "outlined"
                          :default-value (:name @data)
                          :on-change     #(swap! data assoc :name (-> % .-target .-value))}]]
         [activity-skill-info data]
         [ui/grid {:item true :xs 7}
          [ui/input-label "Template"]
          [ui/select {:value     (or (:template-id @data) "")
                      :on-change #(swap! data assoc :template-id (-> % .-target .-value))
                      :style     {:min-width "150px"}}
           (for [template templates]
             ^{:key (:id template)}
             [ui/menu-item {:value (:id template)} (:name template)])]]
         [template-info current-template data]]]
       [ui/card-actions
        [ui/button {:color    "secondary"
                    :style    {:margin-left "auto"}
                    :on-click #(re-frame/dispatch [::state-activity/create-simple-activity @data])}
         "Save"]]])))

(defn game-changer-panel
  []
  (re-frame/dispatch [::state-activity/load-templates])
  (re-frame/dispatch [::state-activity/load-skills])
  [layout {:title "Create Activity"}
   [:div {:style {:height         "100%"
                  :display        "flex"
                  :flex-direction "column"}}
    [simple-activity-info]]])