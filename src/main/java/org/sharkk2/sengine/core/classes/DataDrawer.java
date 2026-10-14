package org.sharkk2.sengine.core.classes;

import org.sharkk2.sengine.core.classes.exceptions.ValueException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class DataDrawer {
    private final Map<String, Object> data = new HashMap<>();
    private final List<String> persistentValues = new ArrayList<>();

    public <T> T getValue(String key, Class<T> type) {
        Object v = data.get(key);
        if (v == null) throw new ValueException("No value for key: " + key);
        return type.cast(v);
    }

    public Number getValueNumber(String key) {
        Object v = data.get(key);
        if (v instanceof Number n) return n;
        throw new ValueException("Value for " + key + " is not a number: " + v);
    }

    public String getValueString(String key) {
        Object v = data.get(key);
        if (v instanceof String n) return n;
        throw new ValueException("Value for " + key + " is not a string: " + v);
    }

    public boolean getValueBoolean(String key) {
        Object v = data.get(key);
        if (v instanceof Boolean n) return n;
        throw new ValueException("Value for " + key + " is not a boolean: " + v);
    }

    public void setValue(String key, Object value) {setValue(key, value, false);}
    public void setValue(String key, Object value, boolean persistant) {
        data.put(key, value);
        if (persistant) persistentValues.add(key);
    }

    public boolean isPersistent(String key) {return persistentValues.contains(key);}
    public void unPersist(String key) {persistentValues.remove(key);}

    public Object getValue(String key) {return data.get(key);}
    public boolean hasValue(String key) {return data.containsKey(key);}
    public void removeValue(String key, boolean force) {
        if (force && persistentValues.contains(key)) return;
        data.remove(key);
    }
    public void removeValue(String key) {removeValue(key, false);}

    public void clear() {clear(false);}
    public void clear(boolean force) {
       if (force) data.clear();
       else data.entrySet().removeIf(entry -> !persistentValues.contains(entry.getKey()));
    }

    public void clear(Predicate<Object> condition) {
        data.values().removeIf(condition);
    }

    public void destroy() {
        clear(true);
        persistentValues.clear();
    }
}
