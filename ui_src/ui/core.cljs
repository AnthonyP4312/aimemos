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
            [:details.buddies 
              [:summary "Buddies"]
              [:a {:on-click #((open-chat-ipc "antsonapalmtree"))} "antsonapalmtree"]]
            [:details.family 
              [:summary "Family"]
              [:a "ur mom"]]
            [:details.buddies 
              [:summary "Co-Workers"]
              [:a "feleap"]]
            [:details.offline 
              [:summary "Offline"]
              [:a "uncool people"]]]]]]])
            


(if-let [root (js/document.getElementById "app-container")]
  (reagent/render
    [:div.window 
      [:div.inner-window
        [window-heading (str "Buddy List")]
        [menu-bar]
        [buddy-list-logo]
        [buddy-list]]]
    root))


(if-let [root (js/document.getElementById "chat-window")]
  (reagent/render
    [:div.window
      [:div.inner-window
        [window-heading (str title)]
        [text-out chat]
        [text-in]
        [chat-sounds]]]
    root))
       

; (.log js/console electron)
