package org.sharkk2.sengine.core.systems.renderer;

import org.sharkk2.sengine.core.systems.components.ModelComponent;

import java.util.*;

public class Batcher {
    private final Map<String, List<ModelComponent>> batches = new HashMap<>();
    private final List<ModelComponent> unbatched = new ArrayList<>();
    private final Deque<List<ModelComponent>> listPool = new ArrayDeque<>();

    public Map<String, List<ModelComponent>> getBatches() {return batches;}
    public int batchCount() {return batches.size();}
    public List<ModelComponent> getUnbatched() {return unbatched;}

    public void group(List<ModelComponent> models) {
        for (List<ModelComponent> list : batches.values()) {
            list.clear();
            listPool.push(list);
        }
        batches.clear();
        unbatched.clear();

        for (ModelComponent model : models) {
            String id = model.batchID;
            if (id == null || id.isEmpty()) {
                unbatched.add(model);
                continue;
            }
            List<ModelComponent> list = batches.get(id);
            if (list == null) {
                list = listPool.isEmpty() ? new ArrayList<>() : listPool.pop();
                batches.put(id, list);
            }
            list.add(model);
        }
    }
}