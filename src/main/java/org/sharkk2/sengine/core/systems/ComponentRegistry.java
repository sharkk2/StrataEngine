package org.sharkk2.sengine.core.systems;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;

import java.util.*;
import java.util.stream.Collectors;

// i should've done this since 3.0.0
public class ComponentRegistry {
    private final Map<Class<? extends Component>, List<Component>> liveInstances = new HashMap<>();
    private final Map<String, Class<? extends Component>> types = new HashMap<>();
    private final Engine engine;

    public ComponentRegistry(Engine engine) {
        this.engine = engine;
    }

    public void register(Component component) {
        liveInstances.computeIfAbsent(component.getClass(), k -> new ArrayList<>()).add(component);
    }

    public void unregister(Component component) {
        List<Component> list = liveInstances.get(component.getClass());
        if (list != null) {
            list.remove(component);


        }
    }

    public <T extends Component> List<T> getComponents(Class<T> type) {
        List<Component> list = liveInstances.get(type);
        if (list == null) {return Collections.emptyList();}
        @SuppressWarnings("unchecked")
        List<T> result = new ArrayList<>((List<T>) list);
        return result;
    }

    public <T extends Component, C extends Collection<T>> C getComponents(Class<T> type, C output) {
        List<Component> list = liveInstances.get(type);
        if (list != null) {
            @SuppressWarnings("unchecked")
            List<T> casted = (List<T>) list;
            output.clear();
            output.addAll(casted);
        }
        return output;
    }

    public void scanComponents() {
        try (ScanResult scan = new ClassGraph().enableClassInfo().scan()) {
            ClassInfoList subclasses = scan.getSubclasses(Component.class.getName());
            for (ClassInfo info : subclasses) {
                if (info.isAbstract()) continue;
                @SuppressWarnings("unchecked")
                Class<? extends Component> type = (Class<? extends Component>) info.loadClass();
                types.put(type.getSimpleName(), type);
            }
        }
        Logger.info("Discovered (" + types.size() + ") component types: " + types.values().stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));
    }

    public int getCount(Class<? extends Component> type) {
        if (!liveInstances.containsKey(type)) return 0;
        return liveInstances.get(type).size();
    }

    public void clearRegistry() {
        liveInstances.clear();
    }
    public Class<? extends Component> resolveType(String name) {
        return types.get(name);
    }

}
