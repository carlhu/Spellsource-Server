package net.demilich.metastone.game.spells.trigger;

import net.demilich.metastone.game.utils.Attribute;
import net.demilich.metastone.game.entities.Entity;
import net.demilich.metastone.game.entities.EntityType;
import net.demilich.metastone.game.events.AttributeAppliedEvent;
import net.demilich.metastone.game.events.GameEvent;
import net.demilich.metastone.game.events.GameEventType;
import net.demilich.metastone.game.spells.desc.trigger.EventTriggerArg;
import net.demilich.metastone.game.spells.desc.trigger.EventTriggerDesc;

public class AttributeAppliedTrigger extends EventTrigger {

	public AttributeAppliedTrigger(EventTriggerDesc desc) {
		super(desc);
	}

	@Override
	protected boolean fire(GameEvent event, Entity host) {
		AttributeAppliedEvent e = (AttributeAppliedEvent) event;
		Attribute attribute = (Attribute) getDesc().get(EventTriggerArg.REQUIRED_ATTRIBUTE);
		EntityType targetEntityType = (EntityType) getDesc().get(EventTriggerArg.TARGET_ENTITY_TYPE);
		return (targetEntityType == null || e.getTarget().getEntityType() == targetEntityType)
				&& e.getAttribute() == attribute;

	}

	@Override
	public GameEventType interestedIn() {
		return GameEventType.ATTRIBUTE_APPLIED;
	}
}
