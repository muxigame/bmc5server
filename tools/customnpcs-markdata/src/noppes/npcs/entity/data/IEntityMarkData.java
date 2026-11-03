package noppes.npcs.entity.data;
import noppes.npcs.controllers.data.MarkData;
/** Transient entity-owned cache; its owner/mark cycle is reclaimed with the entity. */
public interface IEntityMarkData {
    MarkData cnpc$getMarkData();
    void cnpc$setMarkData(MarkData data);
}
