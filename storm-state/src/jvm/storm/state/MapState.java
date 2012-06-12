package storm.state;

import clojure.lang.IPersistentMap;
import clojure.lang.MapEntry;
import clojure.lang.PersistentHashMap;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import storm.state.hdfs.HDFSState;


public class MapState<K, V> extends AbstractMap<K, V> implements State {    
    public static class Factory implements StateFactory {
        @Override
        public State makeState(Map conf, String rootDir, Serializations sers) {
            return new MapState(conf, rootDir, sers);
        }        
    }  
    
    IPersistentMap _cache;
    HDFSState _state;

    @Override
    public void setState(Object snapshot) {
        if(snapshot==null) {
            _cache = PersistentHashMap.EMPTY;
        } else {
            _cache = (IPersistentMap) snapshot;            
        }
    }
    
    @Override
    public Object getSnapshot() {
        return _cache;
    }
    
    public static class Put implements Transaction<MapState> {
        Object key;
        Object value;
        
        //for kryo
        public Put() {
            
        }
        
        public Put(Object k, Object v) {
            key = k;
            value = v;
        }

        @Override
        public Object apply(MapState state) {
           Object ret = state._cache.valAt(key);
           state._cache = state._cache.assoc(key, value);
           return ret;
        }
    }
    
    public static class Remove implements Transaction<MapState> {
        Object key;
        
        //for kryo
        public Remove() {
            
        }
        
        public Remove(Object k) {
            key = k;
        }

        @Override
        public Object apply(MapState state) {
           Object ret = state._cache.valAt(key);
           state._cache = state._cache.without(key);
           return ret;
        }        
    }

    public static class Clear implements Transaction<MapState> {
        @Override
        public Object apply(MapState state) {
           state._cache = PersistentHashMap.EMPTY;
           return null;
        }        
    }    
    
    public MapState(Map conf, String fsLocation) {
        this(conf, fsLocation, new Serializations());
    }
    
    public MapState(Map conf, String fsLocation, Serializations sers) {
        _state = new HDFSState(conf, fsLocation, getSers(sers));
        _state.resetToLatest(this);
    }
    
    public static Serializations getSers(Serializations base) {
        Serializations ret = base.clone();
        return ret.add(Put.class).add(Remove.class).add(Clear.class);
    }

    public void setExecutor(Executor e) {
        _state.setExecutor(e);
    }    
    
    public void commit() {
        _state.commit(this);
    }
    
    public void commit(BigInteger txid) {
        _state.commit(txid, this);
    }    
    
    public void compact() {
        _state.compact(this);
    }
    
    public void compactAsync() {
        _state.compactAsync(this);
    }

    @Override
    public void close() {
        _state.close();
    }

    @Override
    public BigInteger getVersion() {
        return _state.getVersion();
    }    

    @Override
    public V put(K key, V value) {
        return (V) _state.appendAndApply(new Put(key, value), this);
    }

    @Override
    public V remove(Object key) {
        return (V) _state.appendAndApply(new Remove(key), this);
    }

    @Override
    public void clear() {
        _state.appendAndApply(new Clear(), this);
    }
    
    public Iterator<MapEntry> iterator() {
        return _cache.iterator();
    }
    
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }    
    
    @Override
    public int size() {
        return _cache.count();
    }

    @Override
    public boolean containsKey(Object o) {
        return _cache.containsKey(o);
    }    
    
    @Override
    public V get(Object key) {
        return (V) _cache.valAt(key);
    }
}