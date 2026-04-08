package ch.sbb.polarion.extension.xml_repair.service.model;

import com.polarion.alm.shared.api.model.PrototypeEnum;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.alm.tracker.model.baselinecollection.IBaselineCollection;
import com.polarion.platform.persistence.model.IPrototype;
import org.jetbrains.annotations.NotNull;

public enum EntityType {
    COLLECTION,
    DOCUMENT,
    WORKITEM;

    public static EntityType fromPrototype(@NotNull IPrototype prototype) {
        return switch (prototype.getName()) {
            case IBaselineCollection.PROTO -> COLLECTION;
            case IModule.PROTO -> DOCUMENT;
            case IWorkItem.PROTO -> WORKITEM;
            default -> throw new IllegalArgumentException("Unknown entity prototype: " + prototype.getName());
        };
    }

    public PrototypeEnum proto() {
        return switch (this) {
            case COLLECTION -> PrototypeEnum.BaselineCollection;
            case DOCUMENT -> PrototypeEnum.Document;
            case WORKITEM -> PrototypeEnum.WorkItem;
        };
    }
}
