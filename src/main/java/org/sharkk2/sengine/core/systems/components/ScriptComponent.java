package org.sharkk2.sengine.core.systems.components;

import org.sharkk2.sengine.core.classes.Component;

public class ScriptComponent extends Component {
    private final Runnable script;

    public ScriptComponent(Runnable script) {
        this.script = script;
    }

    public Runnable getScript() {return script;}

    @Override
    protected void onObjectAttach() {}

    @Override
    protected void onObjectDetach() {}

    @Override
    protected void onUpdate() {

    }
}
