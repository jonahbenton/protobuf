(ns protobuf.impl.flatland.mapdef
  (:require
    [clojure.java.io :as io]
    [protobuf.impl.flatland.map :as protobuf-map]
    [protobuf.impl.flatland.schema :as protobuf-schema]
    [protobuf.util :as util])
  (:import
    (clojure.lang Reflector)
    (com.google.protobuf CodedInputStream
                         Descriptors$Descriptor
                         GeneratedMessage)
    (java.io InputStream OutputStream)
    (protobuf Extensions
              PersistentProtocolBufferMap
              PersistentProtocolBufferMap$Def
              PersistentProtocolBufferMap$Def$NamingStrategy))
  (:refer-clojure :exclude [map? read]))

(defn mapdef?
  "Is the given object a `PersistentProtocolBufferMap$Def?`"
  [obj]
  (instance? PersistentProtocolBufferMap$Def obj))

(defn ^PersistentProtocolBufferMap$Def mapdef
  "Create a protocol buffer map definition from a string or protobuf class."
  ([map-def]
     (if (or (mapdef? map-def) (nil? map-def))
       map-def
       (mapdef map-def {})))
  ([map-def opts]
     (when map-def
       (let [{:keys [^PersistentProtocolBufferMap$Def$NamingStrategy naming-strategy
                     size-limit]
              :or {naming-strategy PersistentProtocolBufferMap$Def/convertUnderscores
                   size-limit 67108864}} opts ;; 64MiB
             ^Descriptors$Descriptor descriptor
             (if (instance? Descriptors$Descriptor map-def)
               map-def
               (Reflector/invokeStaticMethod ^Class map-def "getDescriptor" (to-array nil)))]
         (PersistentProtocolBufferMap$Def/create descriptor naming-strategy size-limit)))))

(defn create
  "Construct a protobuf of the given map-def."
  ([^PersistentProtocolBufferMap$Def map-def]
     (PersistentProtocolBufferMap/construct map-def {}))
  ([^PersistentProtocolBufferMap$Def map-def m]
     (PersistentProtocolBufferMap/construct map-def m))
  ([^PersistentProtocolBufferMap$Def map-def k v & kvs]
     (PersistentProtocolBufferMap/construct map-def (apply array-map k v kvs))))

(defn mapdef->schema
  "Return the schema for the given mapdef."
  [& args]
  (let [^PersistentProtocolBufferMap$Def map-def (apply mapdef args)]
    (protobuf-schema/field-schema (.getMessageType map-def) map-def)))

(defmulti parse
  "Load a protobuf of the given map-def from a data source.

  Supported data sources are either an array of bytes or an input stream."
  (fn [map-def data & _]
    (type data)))

(defmethod parse (Class/forName "[B")
  ([^PersistentProtocolBufferMap$Def map-def data]
     (when data
       (PersistentProtocolBufferMap/create map-def data)))
  ([^PersistentProtocolBufferMap$Def map-def data ^Integer offset ^Integer length]
     (when data
       (let [^CodedInputStream in (CodedInputStream/newInstance data offset length)]
         (PersistentProtocolBufferMap/parseFrom map-def in)))))

(defmethod parse InputStream
  [^PersistentProtocolBufferMap$Def map-def stream]
  (when stream
    (let [^CodedInputStream in (CodedInputStream/newInstance stream)]
      (PersistentProtocolBufferMap/parseFrom map-def in))))

(defn ^"[B" ->bytes
  "Return the byte representation of the given protobuf."
  [^PersistentProtocolBufferMap$Def map-def m]
  (protobuf-map/->bytes (PersistentProtocolBufferMap/construct map-def m)))

(defn read
  "Lazily read a sequence of length-delimited protobufs of the specified map-def
  from the given input stream."
  [^PersistentProtocolBufferMap$Def map-def in]
  (lazy-seq
   (io!
    (let [^InputStream in (io/input-stream in)]
      (if-let [p (PersistentProtocolBufferMap/parseDelimitedFrom map-def in)]
        (cons p (read map-def in))
        (.close in))))))

(defn write
  "Write the given protobufs to the given output stream, prefixing each with
  its length to delimit them."
  [out & ps]
  (io!
   (let [^OutputStream out (io/output-stream out)]
     (doseq [^PersistentProtocolBufferMap p ps]
       (.writeDelimitedTo p out))
     (.flush out))))

(extend-protocol util/Combiner
  PersistentProtocolBufferMap
  (combine-onto [^PersistentProtocolBufferMap this other]
    (.append this other)))

(def ^{:doc "A convenience alias for `util/combine`"}
  combine #'util/combine)
