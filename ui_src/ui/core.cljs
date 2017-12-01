(ns ui.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.format :refer [format]]
            [clojure.string :as string :refer [split-lines]]
            [clojure.set :refer [difference union intersection select]]))

(def electron (js/require "electron"))
(def remote (.-remote electron))
(def ipc (.-ipcRenderer electron))
(def title (.-title (.-meta (.-browserWindowOptions (.-webContents (.getCurrentWindow remote))))))
(def metadata (.-meta (.-browserWindowOptions (.-webContents (.getCurrentWindow remote)))))
(def this (.getCurrentWindow remote))
(def root (js/document.getElementById "container"))



; (defn now [] "date")
(defn now [] 
  (let [date (js/Date.)]
    (format "%02d:%02d:%02d" (.getHours date)
                             (.getMinutes date)
                             (.getSeconds date))))

  
                             
(defn by-id [id] (js/document.getElementById id))
(defn log [message] (js/console.log message))


(def join-lines (partial string/join "\n"))

(enable-console-print!)

(defonce chat (atom []))    
(defonce buddies (atom #{}))    

;; Some test data for the buddy list
(reset! buddies #{{:id 1 :username "antsonapalmtree" :group "Buddies" :status "online"}
                  {:id 2 :username "jakeman" :group "Buddies" :status "online"} 
                  {:id 3 :username "ryaz" :group "Buddies" :status "online"} 
                  {:id 4 :username "YUKI HIMEKAWA" :group "Buddies" :status "online"} 
                  {:id 5 :username "nice" :group "Family" :status "online"} 
                  {:id 6 :username "sachiko" :group "Buddies" :status "online"} 
                  {:id 7 :username "feleap" :group "Co-Workers" :status "online"} 
                  {:id 8 :username "akari" :group "Buddies" :status "offline"}
                  {:id 9 :username "binaryman" :group "Buddies" :status "offline"}})


(defn open-chat-ipc [username]
  (.sendSync ipc "open-chat" (clj->js {:username username})))

(defn open-buddy-list-ipc [meta]
  (.sendSync ipc "open-buddy-list" (clj->js meta)))

(defn open-login-window-ipc [meta]
  (.sendSync ipc "open-login-window" (clj->js meta)))

(defn open-dialup-splash-ipc [meta]
  (.sendSync ipc "open-dialup-splash" (clj->js meta)))

(defn scroll-down [^js/Event e]
  (log "scrolling!")
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

(defn send-message [^js/Event e]
  (log "sending message!!")
  (.preventDefault e)
  (.play (by-id "messageout"))
  (let [ message (.-value (js/document.getElementById "text-in"))]
    ; (.log js/console message)
    (swap! chat conj {:id (.now js/Date) :ts (now) :author "tanners" :message message})
    (set! (.-value (js/document.getElementById "text-in")) "")))
    
(defn away-effect [username])

(defn ^:export logout-effect [username]  
  (.play (by-id "closedoor"))  
  (js/setTimeout
    #(set! (.-visibility (.-style (by-id (str username "closedoor")))) "visible")
    20)
  (js/setTimeout 
    #(set! (.-visibility (.-style (by-id (str username "closedoor")))) "hidden")
    5000))  

(defn ^:export login-effect [username]
  (.play (by-id "opendoor"))  
  (js/setTimeout
    #(set! (.-visibility (.-style (by-id (str username "opendoor")))) "visible")
    20)
  (js/setTimeout 
    #(set! (.-visibility (.-style (by-id (str username "opendoor")))) "hidden")
    5000))

(defn buddy-change-status [user status]
  (reset! buddies (-> (difference @buddies user)
                      (conj ,,, (assoc user :status status))))  
  (case status                        
    "online" (login-effect (:username user))
    "offline" (logout-effect (:username user))
    "away" (away-effect (:username user))))

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
          (for [{:keys [id ts author message]} @chat]
            ^{:key id} [:div {:id id :on-load scroll-down} ;;(reduce + [author " : " message])
                        [:span.ts (str "[" ts "] ")]  
                        [:span.screen-name author]
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
  [:textarea.text-in {:id "text-in" :on-key-press 
                      (fn [e]
                        (log (.-key e))
                        (when-not (.-shiftKey e)
                          (log "no shift here")
                          (when (= (.-key e) "Enter")    
                            (send-message e))))}]])

(defn splash-sounds []
  [:div#splash-sounds
    [:audio {:id "dialup" :src "sounds/dialup.ogg"}]])

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
  (let [filtered-buddies (filter #(= group-name (:group %)) @buddies)
        online-buddies (filter #(not= "offline" (:status %)) filtered-buddies)]
    [:details
      [:summary (str group-name " ( " (count online-buddies) "/" (count filtered-buddies) " )")]
      (for [{:keys [username]} online-buddies]
       [buddy username])]))

(defn offline-buddies []
  (let [filtered-buddies (filter #(= "offline" (:status %)) @buddies)]        
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
            [buddy-group "Buddies"]
            [buddy-group "Family"]
            [buddy-group "Co-Workers"]
            [offline-buddies]]]]]])

(defn login-form []
  [:form.login-form
    [:div.username-container
      [:div.username-item {:for "username"} "Screen Name"]
      [:span#username-border.username-item
        [:input#username]]]
    [:div.password-container
      [:label.password-item {:for "password"} "Password      "]
      [:span#password-border.password-item
        [:input#password {:type "password"}]]]])
    ; [:div.login-checks
    ;   [:input#save-password {:type "checkbox"}]
    ;   [:label {:for "save-password"} "Save password"]
    ;   [:input#auto-login {:type "checkbox"}]
    ;   [:label {:for "auto-login"} "Auto-login"]]])

(defn render-buddy-list-window []  
  (reagent/render
    [:div.window 
      [:div.inner-window
        [buddy-sounds]
        [window-heading (str (.-username metadata) " 's Buddy List")]
        [menu-bar ["My AIM" "People" "Help"]]
        [buddy-list-logo]
        [buddy-list]]]
    root))  
(defn render-chat-window []
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
  (reagent/render
    [:div.window
      [:div.inner-window
        [window-heading (str title)]
        [:div.login-container
          [:img.login-logo {:src "img/login-logo.png"}]
          [horizontal-rule 3]
          [login-form]]]]
          
    root))
(defn render-dialup-splash-window [])

(println metadata)
(println title)
(case (.-class metadata) 
  "dialup-splash" (render-dialup-splash-window)
  "chat-window" (render-chat-window)
  "login-window" (render-login-window)
  "buddy-list" (render-buddy-list-window))
; (.log js/console electron)
