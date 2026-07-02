package org.sharkk2.sengine.core.systems.components;

import org.sharkk2.sengine.core.classes.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ScriptComponent extends Component {
    private final Consumer<ScriptContext> script;
    public int runFrameInterval = 0;
    public int lastRunFrame = 0;
    public final Map<String, Object> states = new HashMap<>();
    private final ScriptContext context;

    public static class ScriptContext {
        private final ScriptComponent scomp;
        public ScriptContext(ScriptComponent scomp) {this.scomp = scomp;}

        public void state(String key, Object value) {scomp.states.put(key, value);}
        @SuppressWarnings("unchecked")
        public <T> T readState(String key) {
            return (T) scomp.states.get(key);
        }

        @SuppressWarnings("unchecked")
        public <T> T readState(String key, T def) {
            return (T) scomp.states.getOrDefault(key, def);
        }

        public void removeState(String key) {scomp.states.remove(key);}
        public void clearStates() {scomp.states.clear();}

        public void setFrameRunInterval(int v) {scomp.runFrameInterval = v;}
        public int getFrameRunInterval() {return scomp.runFrameInterval;}


    }

    public ScriptComponent(Consumer<ScriptContext> script) {
        this.script = script;
        this.context = new ScriptContext(this);
    }

    public ScriptComponent(Consumer<ScriptContext> script, int frameInterval) {
        this.script = script;
        this.runFrameInterval = frameInterval;
        this.context = new ScriptContext(this);
    }

    public Consumer<ScriptContext> getScript() {return script;}
    public ScriptContext getContext() {return context;}

    @Override
    protected void onObjectAttach() {}

    @Override
    protected void onObjectDetach() {}

    @Override
    protected void onUpdate() {

    }
}
