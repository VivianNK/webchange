(ns webchange.interpreter.variables.events
  (:require
    [clojure.set :refer [union]]
    [re-frame.core :as re-frame]
    [day8.re-frame.tracing :refer-macros [fn-traced]]
    [webchange.common.events :as e]
    [webchange.state.lessons.subs :as lessons]
    [webchange.interpreter.variables.core :as core]))

(e/reg-simple-executor :lesson-var-provider ::execute-lesson-var-provider)
(e/reg-simple-executor :vars-var-provider ::execute-vars-var-provider)
(e/reg-simple-executor :test-var ::execute-test-var)        ;; Not used
(e/reg-simple-executor :test-var-scalar ::execute-test-var-scalar)
(e/reg-simple-executor :test-var-list ::execute-test-var-list)
(e/reg-simple-executor :test-value ::execute-test-value)
(e/reg-simple-executor :case ::execute-case)
(e/reg-simple-executor :counter ::execute-counter)
(e/reg-simple-executor :calc ::execute-calc)
(e/reg-simple-executor :set-variable ::execute-set-variable)
(e/reg-simple-executor :set-progress ::execute-set-progress)
(e/reg-simple-executor :copy-variable ::execute-copy-variable)
(e/reg-simple-executor :clear-vars ::execute-clear-vars)
(e/reg-simple-executor :map-value ::execute-map-value)

(re-frame/reg-event-fx
  ::execute-set-variable
  (fn [{:keys [db]} [_ {:keys [var-name var-value] :as action}]]
    "Execute `set-variable` action - allow to set value to corresponding variable.

    Action params:
    :var-name - variable name to set.
    :var-value - value to set.

    Example:
    {:type 'set-variable'
     :var-name 'answer-clickable'
     :var-value false}"
    (core/set-variable! var-name var-value)
    {:dispatch (e/success-event action)}))

(re-frame/reg-event-fx
  ::execute-set-progress
  (fn [{:keys [db]} [_ {:keys [var-name var-value] :as action}]]
    "Execute `set-progress` action - allow to set variables to student progress data.

    Action params:
    :var-name - which will be set.
    :var-value - value to set.

    Example:
    {:type 'set-progress',
     :var-name 'last-location',
     :from-params [{:param-property 'scene-id', :action-property 'var-value'}]}"
    {:db         (core/set-progress db var-name var-value)
     :dispatch-n (list (e/success-event action) [:progress-data-changed])}))

(re-frame/reg-event-fx
  ::execute-copy-variable
  (fn [{:keys [db]} [_ {:keys [var-name from] :as action}]]
    "Execute `copy-variable` action - allow to copy one variable value to another.

    Action params:
    :from - source variable name
    :var-name - target variable name

    Example:
    {:type 'copy-variable'
     :var-name 'current-word'
     :from 'item-2'}"
    (let [var-value (core/get-variable from)]
      (core/set-variable! var-name var-value)
      {:dispatch (e/success-event action)})))

(re-frame/reg-event-fx
  ::execute-map-value
  (fn [{:keys [db]} [_ {:keys [var-name value from to] :as action}]]
    "Execute `map-value` action - This method find index of element in 'from' array with 'value',
    then takes element from to array with the same index and store that information in variable with name `var-name`.

    Action params:
    :to - target array of variable, which will be used as source
    :from - source array of variable names, where values will be searched
    :var-name - variable name where data will be stored
    :value - will be used to compare with elements of from array

    Example:
    {:to ['letter1' 'letter2' 'letter3' 'letter4'],
     :from ['player1' 'player2' 'player3' 'player4'],
     :type 'map-value',
     :from-var [{:var-name 'pair-target-3', :action-property 'value'}],
     :var-name 'target-letter'}"
    (let [var-value (get to (.indexOf from value))]
      (core/set-variable! var-name var-value)
      {:dispatch (e/success-event action)})))

(re-frame/reg-event-fx
  ::clear-vars
  (fn [{:keys [db]} [_ {:keys [keep-running]}]]
    (core/clear-vars! keep-running)))

(re-frame/reg-event-fx
  ::execute-clear-vars
  (fn [{:keys [db]} [_ action]]
    "Execute `clear-vars` action - allow to drop all variables and providers.

    Example:
    {:type 'clear-vars'}
    "
    (core/clear-vars! true)
    {:dispatch-n (list (e/success-event action))}))

(re-frame/reg-event-fx
  ::execute-vars-var-provider
  (fn [{:keys [db]} [_ {:keys [from variables provider-id on-end] :as action}]]
    "Execute `vars-var-provider` action - provides one concept from variables list for each call.

    Action params:
    :from - array of variables names which will be copied to target variables
    :shuffled - if true return a random permutation of values.
    :variables - array target variables which will be assigned
    :provider-id - variable name where extracted data will be stored.
    :on-end - on end event handler. Will be executed when no additional items to assign to variable.

    Example:
    {:type 'vars-var-provider',
     :from ['vaca-voice-next-picture-1' 'vaca-voice-next-picture-2' 'vaca-voice-next-picture-3' 'vaca-voice-next-picture-4'],
     :shuffled  true,
     :variables ['current-vaca-voice-next']}
    "
    (let [items (->> from
                     (map (fn [var-name]
                            (cond-> (core/get-variable var-name)
                                    provider-id (assoc :id var-name)))))
          has-next (core/has-next items provider-id)
          scene-id (:current-scene db)
          on-end-action (->> on-end
                             keyword
                             (vector :scenes scene-id :actions)
                             (get-in db))]
      (if has-next
        (do
          (core/provide! items variables provider-id action)
          {:dispatch (e/success-event action)})
        {:dispatch [::e/execute-action on-end-action]}))))

;TODO: level get lessons from levels
(re-frame/reg-event-fx
  ::execute-lesson-var-provider
  (fn [{:keys [db]} [_ {:keys [from variables provider-id on-end] :as action}]]
    "Execute `lesson-var-provider` action - provides one concept from a lesson set for each call

    Action params:
    :from - lesson set, which will be source variable
    :provider-id - variable name where extracted data will be stored.
    :shuffled - if true return a random permutation of values.
    :variables - list variable to assign
    :on-end - on end event handler. Will be executed when no additional items to assign to variable.

    Example:
    {:type 'lesson-var-provider',
     :from 'concepts',
     :provider-id 'words-set',
     :shuffled  false,
     :variables   ['item-1' 'item-2' 'item-3']}"
    (let [items (lessons/lesson-dataset-items db from)
          has-next (core/has-next items provider-id action)
          scene-id (:current-scene db)
          on-end-action (->> on-end
                             keyword
                             (vector :scenes scene-id :actions)
                             (get-in db))]
      (if has-next
        (do
          (core/provide! items variables provider-id action)
          {:dispatch (e/success-event action)})
        {:dispatch [::e/execute-action on-end-action]}))))

(re-frame/reg-event-fx
  ::execute-test-var
  (fn [{:keys [db]} [_ {:keys [var var-name property success fail] :as action}]]
    "Execute `test-var` action - check if a variable is equal to the specific value.

    Action params:
    :success - action to execute if the comparison is successful. Can be represented as an action name (string) or action data.
    :fail- action to execute if the comparison is failed. Can be represented as an action name (string) or action data.
    :var-name - variable name that we want to test.
    :var - object to compare
    :property - property to check in variable, the same property will be used for both comparable objects

    Example:
    {:fail 'pick-wrong',
     :type 'test-var',
     :success 'pick-correct',
     :property 'id',
     :var-name 'current-word'}"
    (let [test (core/get-variable var-name)
          key (keyword property)
          success (e/get-action success db action)
          fail (e/get-action fail db action)]
      (if (= (key var) (key test))
        {:dispatch-n (list [::e/execute-action success] (e/success-event action))}
        {:dispatch-n (list [::e/execute-action fail] (e/success-event action))}))))

(defn cond-action [db {:keys [flow-id action-id] :as action} params]
  (let [action-data (if (string? params) (e/get-action params db action) params)]
    (cond-> action-data
            flow-id (assoc :flow-id flow-id)
            action-id (assoc :action-id action-id)
            :always (e/with-prev action))))

(re-frame/reg-event-fx
  ::execute-test-var-scalar
  (fn [{:keys [db]} [_ {:keys [value var-name success fail] :as action}]]
    "Execute `test-var-scalar` action - compare variable value with test value.

    Action params:
    :var-name - variable name that we want to test.
    :value - value to compare with.
    :success - action to execute if the comparison is successful. Can be represented as an action name (string) or action data.
    :fail- action to execute if the comparison is failed. Can be represented as an action name (string) or action data.

    Example:
    {:type     'test-var-scalar',
     :var-name 'current-box',
     :value    'box1',
     :success  'first-word',
     :fail     'pick-wrong'}"
    (let [test (core/get-variable var-name)]
      (if (= value test)
        {:dispatch [::e/execute-action (cond-action db action success)]}
        {:dispatch [::e/execute-action (cond-action db action fail)]}))))

(re-frame/reg-event-fx
  ::execute-test-value
  [e/event-as-action e/with-vars]
  (fn [{:keys [db]} {:keys [value1 value2 success fail] :as action}]
    "Execute `test-value` action - compare two values.
    Could be useful for comparing some value with the parameter's value. Use `:from-params` in this case.

    Action params:
    :value1 - the first value to compare.
    :value2 - the second value to compare.
    :success - action to execute if the comparison is successful. Can be represented as an action name (string) or action data.
    :fail- action to execute if the comparison is failed. Can be represented as an action name (string) or action data.
    
    Example:
    {:type        'test-value'
     :from-params [{:action-property 'value1' :param-property 'target'}]
     :from-var    [{:action-property 'value2' :var-name 'current-box'}]
     :success     'pick-correct'
     :fail        'pick-wrong'}"
    (if (= value1 value2)
      {:dispatch-n (list [::e/execute-action (cond-action db action success)])}
      (if fail
        {:dispatch-n (list [::e/execute-action (cond-action db action fail)])}
        {:dispatch-n (list (e/success-event action))}
        ))))

(re-frame/reg-event-fx
  ::execute-test-var-list
  (fn-traced [{:keys [db]} [_ {:keys [values var-names success fail] :as action}]]
    "Execute `test-var-list` action - compare variables list value with test values list.
    Variables are compared with values according to their positions in the list.

    Action params:
    :var-names - list of variables names that we want to test.
    :values - list values to compare with.
    :success - action name to execute if the comparison is successful.
    :fail - action name to execute if the comparison is failed.

    Example:
    {:type      'test-var-list',
     :success   'mari-voice-finish',
     :values    [true true true],
     :var-names ['story-1-passed' 'story-2-passed' 'story-3-passed']}"
    (let [test (map core/get-variable var-names)]
      (if (= values test)
        {:dispatch-n (list [::e/execute-action (e/get-action success db action)] (e/success-event action))}
        (if fail
          {:dispatch-n (list [::e/execute-action (e/get-action fail db action)] (e/success-event action))}
          {:dispatch-n (list (e/success-event action))})))))

(re-frame/reg-event-fx
  ::execute-case
  [e/event-as-action e/with-vars]
  (fn [{:keys [db]} {:keys [value options] :as action}]
    "Execute `case` action - action when variable value equal one of option key value.

    Action params:
    :options - map of keys and actions. Keys will be used to compare with variable value. If value is equal action will be executed.
    :value - parameter which will be used to compare

    Example:
    {:type 'case',
     :options {:box1 {:id 'go-to-box2-line-down', :type 'action'},
               :box2 {:id 'stay-on-line', :type 'action'},
               :box3 {:id 'go-to-box2-line-up', :type 'action'}},
     :from-var [{:var-name 'current-line', :action-property 'value'}]}"
    (let [success (get options (keyword value))
          default (get options :default)
          ]
      (if default
        (if success
          {:dispatch-n (list [::e/execute-action success] (e/success-event action))}
          {:dispatch-n (list [::e/execute-action default] (e/success-event action))})
        (if value
          {:dispatch-n (list [::e/execute-action success] (e/success-event action))})))))

(re-frame/reg-event-fx
  ::execute-counter
  (fn [{:keys [db]} [_ {:keys [counter-action counter-value counter-id] :as action}]]
    "Execute `` action - .

    Action params:
    :counter-action - action which should be applied to counter variable. Available options: 'increase', 'decrease', 'reset '.
    :counter-id - name of counter variable
    :counter-value - used for reset option. This value will be set to counter.

    Example:
    {:type 'counter'
     :counter-action 'increase'
     :counter-id 'current-stage'}
    "
    (let [fn (case (keyword counter-action)
               :increase inc
               :decrease dec
               :reset (constantly counter-value))]
      (->> counter-id
           (core/get-variable)
           fn
           (core/set-variable! counter-id))
      {:dispatch (e/success-event action)})))

(re-frame/reg-event-fx
  ::execute-calc
  (fn [{:keys [db]} [_ {:keys [var-name operation value-1 value-2] :as action}]]
    "Execute `calc` action - allow to perform simple calculations.

    Action params:
    :operation - operation which will be applied to operands. Availble operations: 'div-ceil', 'div-floor'
    :value-1 - first operand.
    :value-2 - second operand.
    :var-name - variable name to store operation result

    Example:
    {:type 'calc',
     :value-1 30,
     :value-2 13,
     :var-name 'pages-number',
     :operation 'div-ceil'
     }"
    (let [fn (case (keyword operation)
               :div-floor (comp Math/floor /)
               :div-ceil (comp Math/ceil /))]
      (core/set-variable! var-name (fn value-1 value-2))
      {:dispatch (e/success-event action)})))
