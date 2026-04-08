package ch.sbb.polarion.extension.xml_repair.service.model;

import com.polarion.alm.projects.model.IUniqueObject;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.UUID;
import lombok.SneakyThrows;

import java.io.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class IssueMetaInfo implements Serializable {

    public static final String PROJECT_ID = "projectId";
    public static final String MODULE_PATH = "modulePath";
    public static final String ID = "id";
    public static final String REPAIRER = "repairer";

    @Serial
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> data = new HashMap<>();

    private IssueMetaInfo() {
        data.put("uid", UUID.nextUUID());
    }

    public static IssueMetaInfo create(IWorkItem workItem) {
        IssueMetaInfo metaInfo = new IssueMetaInfo();
        metaInfo.data.put(PROJECT_ID, workItem.getProjectId());
        metaInfo.data.put(ID, workItem.getId());
        return metaInfo;
    }

    public static IssueMetaInfo create(IUniqueObject uniqueObject) {
        if (uniqueObject instanceof IModule module) {
            return create(module);
        } else if (uniqueObject instanceof IWorkItem workItem) {
            return create(workItem);
        } else {
            throw new IllegalArgumentException(String.format("Unrecognized object type: %s", uniqueObject.getClass().getName()));
        }
    }

    public static IssueMetaInfo create(IModule module) {
        IssueMetaInfo metaInfo = new IssueMetaInfo();
        metaInfo.data.put(PROJECT_ID, module.getProjectId());
        metaInfo.data.put(MODULE_PATH, module.getRelativePath());
        return metaInfo;
    }

    @SneakyThrows
    public static IssueMetaInfo fromString(String issueMetaInfo) {
        byte[] bytes = Base64.getDecoder().decode(issueMetaInfo);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (IssueMetaInfo) ois.readObject();
        }
    }

    public IssueMetaInfo set(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        return (String) data.get(key);
    }

    public Boolean getBoolean(String key) {
        return (Boolean) data.get(key);
    }

    @SneakyThrows
    public String serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            oos.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        }
    }
}
