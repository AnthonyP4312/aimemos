(ns ui.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.format :refer [format]]
            [clojure.string :as string :refer [split-lines]]))

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
(defonce buddies (atom []))    

;; Some test data for the buddy list
(reset! buddies [{:username "antsonapalmtree" :group "Buddies" :status "online"}
                 {:username "jakeman" :group "Buddies" :status "online"} 
                 {:username "ryaz" :group "Buddies" :status "online"} 
                 {:username "YUKI HIMEKAWA" :group "Buddies" :status "online"} 
                 {:username "nice" :group "Family" :status "online"} 
                 {:username "sachiko" :group "Buddies" :status "online"} 
                 {:username "feleap" :group "Co-Workers" :status "online"} 
                 {:username "akari" :group "Buddies" :status "offline"}
                 {:username "ritz" :group "Buddies" :status "offline"}])


(defn open-chat-ipc [username]
  (.sendSync ipc "open-chat" (clj->js {:username username})))

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

(defn send-message [^js/Event e]
  (log "sending message!!")
  (.preventDefault e)
  (.play (by-id "messageout"))
  (let [ message (.-value (js/document.getElementById "text-in"))]
    ; (.log js/console message)
    (swap! chat conj {:id (.now js/Date) :ts (now) :author "author" :message message})
    (set! (.-value (by-id "text-in")) "")))
    
(defn text-in []
 [:div.outer-text-in 
  [:textarea#text-in {:on-key-press 
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
    
(defn menu-bar []
  [:div.menu-bar
    [:button {:id "my-aim" :on-click my-aim} "My AIM"]
    [:button {:id "people" :on-click people} "People"]
    [:button {:id "help" :on-click help} "Help"]])

(defn buddy-list-logo []
  [:div.buddy-list-logo
    [:img {:src "https://puu.sh/yvB46/41b59ba73e.jpg"}]])


(defn buddy [username]
  [:div.buddy
    [:img.opendoor {:src "img/opendoor.png" :id (str username "opendoor")}]              
    [:img.closedoor {:src "img/closedoor.png" :id (str username "closedoor")}]   
    [:a {:on-click #((open-chat-ipc username))} username]])    

(defn buddy-group [group-name]
  (let [filtered-buddies 
        (filter #(and (not= "offline" (:status %)) (= group-name (:group %))) @buddies)]
    [:details
      [:summary group-name
       (for [{:keys [username]} filtered-buddies]
        [buddy username])]]))


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
            [buddy-group "Co-Workers"]]]]]])

(defn render-buddy-list-window []  
  (reagent/render
    [:div.window 
      [:div.inner-window
        [window-heading (str "Buddy List")]
        [menu-bar]
        [buddy-list-logo]
        [buddy-list]]]
    root))  
(defn render-chat-window []
  (reagent/render
    [:div.window
      [:div.inner-window
        [window-heading (str title)]
        [text-out chat]
        [text-in]
        [chat-sounds]]]
    root))
(defn render-login-window [])
(defn render-dialup-splash-window [])

(log metadata)
(case (.-class metadata) 
  "dialup-splash" (render-dialup-splash-window)
  "chat" (render-chat-window)
  "login" (render-login-window)
  "buddy-list" (render-buddy-list-window))
; (.log js/console electron)
