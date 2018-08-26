(ns ui.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.format :refer [format]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! >! put! chan]]
            [clojure.string :as string :refer [split-lines]]
            [clojure.set :refer [difference union intersection select]]
            [clojure.data :refer [diff]]))

(enable-console-print!)

(def electron (js/require "electron"))
(def wsClient (js/require "ws"))
(def remote (.-remote electron))
(def ipc (.-ipcRenderer electron))
(def metadata (.-meta
               (.-browserWindowOptions
                (.-webContents (.getCurrentWindow remote)))))
(def title (.-title metadata))
(def this (.getCurrentWindow remote))
(def root (js/document.getElementById "container"))

(defn now
  "Returns a timestamp formatted as hh:MM:SS"
  []
  (let [date (js/Date.)]
    (format "%02d:%02d:%02d" (.getHours date)
                             (.getMinutes date)
                             (.getSeconds date))))

(defn by-id [id] (js/document.getElementById id))
(defn log [message] (js/console.log message))
(defn stringify
  "Takes a clojure map, returns a Stringified json object"
  [clj-map]
  (js/JSON.stringify (clj->js clj-map)))

(def join-lines (partial string/join "\n"))


(defonce chat (atom []))
(defonce buddies (atom ()))
(defonce ws (atom {}))


(reset! buddies (js->clj (.-buddies metadata) :keywordize-keys true))

(declare login-effect logout-effect away-effect)
(defn buddy-effects
  [k atom old new]
  (when-not (empty? old)
    (let [[left right same] (diff old new)]
      (doseq [buddy right]
        (case (:status buddy)
          "ONLINE" (login-effect (:username buddy))
          "OFFLINE" (logout-effect (:username buddy))
          "AWAY" (away-effect (:username buddy)))))))

(add-watch buddies :effects buddy-effects)

(defn group-list
  "returns a set of all groups in a buddylist"
  [buddies]
  (set (map #(:groupname %) buddies)))

(defn create-socket-ipc
  []
  (let [username (.-value (by-id "username"))
        password (.-value (by-id "password"))]
    (.send ipc "create-socket" (str username ":" password))))

(defn open-chat-ipc [username]
  (.sendSync ipc "open-chat" (clj->js {:username username})))

(defn open-buddy-list-ipc [meta]
  (.sendSync ipc "open-buddy-list" (clj->js meta)))

(defn open-login-window-ipc [meta]
  (.sendSync ipc "open-login-window" (clj->js meta)))

(defn open-dialup-splash-ipc [meta]
  (.sendSync ipc "open-dialup-splash" (clj->js meta)))

(defn scroll-down [^js/Event e]
  (set! (.-scrollTop (by-id "text-out")) (.-scrollHeight (by-id "text-out"))))

(defn close-window []
  (.close this))

(defn minimize-window []
  (if (.isMinimized this) (.restore this) (.minimize this)))

(defn maximize-window []
  (if (.isMaximized this) (.unmaximize this) (.maximize this)))

(defn my-aim [])

(defn people [])

(defn help [])

(defn login-help [^js/Event e]
  (.preventDefault e))

(defn login-setup [^js/Event e]
  (.preventDefault e))

(defn send-message [^js/Event e]
  (.preventDefault e)

  (.play (by-id "messageout"))
  (let [message (.-value (js/document.getElementById "text-in"))]
    (when-not (string/blank? message)
      (.send ipc "socket-action"
             (stringify {:method "send-message"
                         :params {:to (.-title metadata)
                                  :from (.-username metadata)
                                  :message message}}))
      (swap! chat conj {:id (.now js/Date)
                        :ts (now)
                        :author (.-username metadata)
                        :message message})
      (set! (.-value (js/document.getElementById "text-in")) ""))))

(defn receive-message [author message]
  (println "recieved a message!" author message)
  (.play (by-id "messagein"))
  (swap! chat conj
         {:id (.now js/Date)
          :ts (now)
          :other-guy author
          :message message}))

(defn away-effect [username])

(defn logout-effect [username]
  (.play (by-id "closedoor"))
  (js/setTimeout
   #(set! (.-visibility (.-style (by-id (str username "closedoor")))) "visible")
   20)
  (js/setTimeout
   #(set! (.-visibility (.-style (by-id (str username "closedoor")))) "hidden")
   5000))

(defn login-effect [username]
  (.play (by-id "opendoor"))
  (js/setTimeout
   #(set! (.-visibility (.-style (by-id (str username "opendoor")))) "visible")
   20)
  (js/setTimeout
   #(set! (.-visibility (.-style (by-id (str username "opendoor")))) "hidden")
   5000))

(defn dialup-script []
  (.play (by-id "dialup"))
  (js/setTimeout #(set! (.-src (by-id "splash")) "img/dialup-part2.png") 20506)
  (js/setTimeout #(set! (.-src (by-id "splash")) "img/dialup-part3.png") 23012)
  (js/setTimeout #(open-login-window-ipc {}) 25500)
  (js/setTimeout close-window 26000))

(defn validate-login [^js/Event e]
  (.preventDefault e)
  (create-socket-ipc))

(defn horizontal-rule [size]
  (println size)
  [:div.horizontal-rule
   [:div.upper-rule-border {:style {:height size}}]
   [:div.lower-rule-border {:style {:height size}}]])

(defn window-heading [title]
  [:div.title-bar title
   [:img.title-icon {:src "img/aim.png"}]
   [:div.title-btn
    [:input {:on-click minimize-window :type "image" :src "img/min.png" :id "btn-min"}]
    [:input {:on-click maximize-window :type "image" :src "img/max.png" :id "btn-max"}]
    [:input {:on-click close-window :type "image" :src "img/close.png" :id "btn-close"}]]])

(defn text-out [chat]
  (reagent/create-class
   {:component-did-update scroll-down
    :reagent-render
    (fn [chat]
      [:div.text-out
       [:div#text-out.inner-text-out
        (for [{:keys [id ts author message other-guy]} @chat]
          ^{:key id} [:div {:id id :on-load scroll-down}
                      [:span.ts (str "[" ts "] ")]
                      [:span.screen-name author]
                      [:span.other-screen-name other-guy]
                      [:span ": "]
                      [:span#message message]])]])}))

(defn text-options []
  [:div#text-options-container.outer-border
   [:div.inner-border
    [:div.text-options
     [:span.color-picker
      [:input.options {:type "image" :src "img/font-color.png"}]
      [:input.options {:type "image" :src "img/font-background-color.png"}]]
     [:span.text-size
      [:input.options {:type "image" :src "img/lower.png"}]
      [:input.options {:type "image" :src "img/reset.png"}]
      [:input.options {:type "image" :src "img/upper.png"}]]
     [:span.text-decoration
      [:input.options {:type "image" :src "img/bold.png"}]
      [:input.options {:type "image" :src "img/italic.png"}]
      [:input.options {:type "image" :src "img/underline.png"}]]
     [:span.insert
      [:input.options {:type "image" :src "img/hyperlink.png"}]
      [:input.options {:type "image" :src "img/image.png"}]
      [:input.options {:type "image" :src "img/email.png"}]
      [:input.options {:type "image" :src "img/emoji.png"}]]]]])

(defn chat-buttons []
  [:div#chat-buttons-container.outer-border
   [:div.inner-border
    [:div.chat-buttons
     [:div.warn-block
      [:input.chat-button {:type "image" :src "img/warn.png"}]
      [:input.chat-button {:type "image" :src "img/block.png"}]]
     [:div.buddy-actions
      [:input.chat-button {:type "image" :src "img/add-buddy.png"}]
      [:input.chat-button {:type "image" :src "img/talk.png"}]
      [:input.chat-button {:type "image" :src "img/get-info.png"}]]
     [:div.send-button
      [:input.chat-button {:type "image" :src "img/send.png" :on-click send-message}]]]]])

(defn text-in []
  [:div.outer-text-in
   [:textarea.text-in {:id "text-in" :on-key-down
                       (fn [e]
                         (println (.-key e))
                         (when-not (.-shiftKey e)
                           (log "no shift here")
                           (when (= (.-key e) "Enter")
                             (send-message e))))}]])

(defn splash-sounds []
  [:div#splash-sounds
   [:audio {:id "dialup" :src "sound/dialup.ogg"}]])

(defn buddy-sounds []
  [:div#buddy-sounds
   [:audio {:id "opendoor" :src "sound/opendoor.ogg"}]
   [:audio {:id "closedoor" :src "sound/closedoor.ogg"}]
   [:audio {:id "cash" :src "sound/cash.ogg"}]
   [:audio {:id "moo" :src "sound/moo.ogg"}]
   [:audio {:id "call" :src "sound/call.ogg"}]])

(defn chat-sounds []
  [:div#chat-sounds
   [:audio {:id "messagein" :src "sound/messagein.ogg"}]
   [:audio {:id "messageout" :src "sound/messageout.ogg"}]])

(defn menu-bar [buttons]
  [:div.menu-bar-container
   [:div.menu-bar
    (for [text buttons]
      [:button text])]
   [:div.menu-bar-border]])

(defn buddy-list-logo []
  [:div.buddy-list-logo
   [:img {:src "img/aimlogo.png"}]])

(defn buddy [username]
  [:div.buddy
   [:img.opendoor {:src "img/opendoor.png" :id (str username "opendoor")}]
   [:img.closedoor {:src "img/closedoor.png" :id (str username "closedoor")}]
   [:a {:on-click #((open-chat-ipc username))} username]])

(defn buddy-group [group-name]
  (let [filtered-buddies (filter #(= group-name (% :groupname)) @buddies)
        online-buddies (filter #(not= "OFFLINE" (% :status)) filtered-buddies)]
    [:details
     [:summary (str group-name
                    " ( "
                    (count online-buddies)
                    "/"
                    (count filtered-buddies)
                    " )")]
     (for [{:keys [username]} online-buddies]
       [buddy username])]))

(defn offline-buddies []
  (println "buddies" @buddies (count @buddies))
  (let [filtered-buddies (filter #(= "OFFLINE" (:status %)) @buddies)]
    [:details.offline
     [:summary (str "Offline ( " (count filtered-buddies) "/" (count @buddies) " )")]
     (for [{:keys [username]} filtered-buddies]
       [buddy username])]))

(defn buddy-list []
  [:div.buddy-list-container
   [:div.online
    [:button#online "Online"]]
   [:div.list-setup
    [:button#list-setup "List Setup"]]
   [:div.buddy-window
    [:div.inner-buddy-window
     [:div.buddy-list
      [:div.inner-buddy-list
       (for [name (sort (group-list @buddies))]
         [buddy-group name])
       [offline-buddies]]]]]])

(defn login-form []
  [:form.login-form {:on-submit validate-login}
   [:div.username-container
    [:div.username-item {:for "username"} "Screen Name"]
    [:span#username-border.username-item
     [:input#username]]]
   [:div.password-container
    [:label.password-item {:for "password"} "Password      "]
    [:span#password-border.password-item
     [:input#password {:type "password"}]]]
   [:div.left-block
    [:input#save-password {:type "checkbox"}]
    [:label {:for "save-password"} "Save password"]
    [:div.left-buttons
     [:input {:type "image" :id "help" :src "img/help.png" :on-click login-help}]
     [:input {:type "image" :id "setup" :src "img/setup.png" :on-click login-setup}]]]
   [:div.right-block
    [:input#auto-login {:type "checkbox"}]
    [:label {:for "auto-login"} "Auto-login"]
    [:div.right-buttons
     [:input {:type "image" :id "sign-on" :src "img/sign-on.png"}]]]])

(defn render-buddy-list-window []
  (.on ipc "buddy-list"
       (fn [event, info]
         (println "buddylist changed: " info)
         (reset! buddies (js->clj info))))

  (go (let [blist (<! (http/get
                       (str "http://69.164.212.77:80/aim/buddies-by-user/"
                            (.-username metadata))))]
        (println "got some buddies" blist)
        (reset! buddies (:body blist))))

  (reagent/render
   [:div.window
    [:div.inner-window
     [buddy-sounds]
     [window-heading (str (.-username metadata) "'s Buddy List")]
     [menu-bar ["My AIM" "People" "Help"]]
     [buddy-list-logo]
     [buddy-list]]]
   root))

(defn render-chat-window []

  (.on ipc (str "chat-" title)
       (fn [event, info]
         (println "new chat message: " info)
         (receive-message title info)))

  (reagent/render
   [:div.window
    [:div.inner-window
     [window-heading (str title)]
     [menu-bar ["File" "Edit" "Insert" "People"]]
     [:div.chat-container
      [text-out chat]
      [text-options]
      [text-in]
      [chat-buttons]]
     [chat-sounds]]]
   root))

(defn render-login-window []

  (.on ipc "login-success"
       (fn [event, info]
         (println "GREAT SUCCESS" info)
         (open-buddy-list-ipc (.-value (by-id "username")))
         (close-window)))

  (.on ipc "login-failure"
       (fn [event, info]
         (println "rip + moo")
         (.play (by-id "moo"))))

  (reagent/render
   [:div.window
    [buddy-sounds]
    [:div.inner-window
     [window-heading "Sign On"]
     [:div.login-container
      [:img.login-logo {:src "img/login-logo.png"}]
      [horizontal-rule 3]
      [login-form]]
     [:div.version "Version: 0.0.1"]]]
   root))
(defn render-dialup-splash-window []
  (reagent/render
   [:div.splash-container
    [splash-sounds]
    [:img.splash {:id "splash" :src "img/dialup-part1.png" :on-load dialup-script}]]
   root))



;; (println metadata)
;; (println title)
(case (.-class metadata)
  "dialup-splash" (render-dialup-splash-window)
  "chat-window" (render-chat-window)
  "login-window" (render-login-window)
  "buddy-list" (render-buddy-list-window))
