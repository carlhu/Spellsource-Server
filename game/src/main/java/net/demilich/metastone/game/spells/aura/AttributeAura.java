package net.demilich.metastone.game.spells.aura;

import net.demilich.metastone.game.spells.AddAttributeSpell;
import net.demilich.metastone.game.spells.RemoveAttributeSpell;
import net.demilich.metastone.game.spells.desc.aura.AuraArg;
import net.demilich.metastone.game.spells.desc.aura.AuraDesc;
import net.demilich.metastone.game.spells.desc.filter.EntityFilter;
import net.demilich.metastone.game.spells.trigger.WillEndSequenceTrigger;
import net.demilich.metastone.game.utils.Attribute;

/**
 * Grants an {@link AuraArg#ATTRIBUTE} to the specified targets.
 * <p>
 * Always use an attribute prefixed with {@code AURA_}, like {@link Attribute#AURA_LIFESTEAL}, to prevent inadvertent
 * interactions with other cards that <b>permanently</b> grant an entity an attribute.
 * <p>
 * For example, to give all friendly beasts charge:
 * <pre>
 *    {
 *     "class": "AttributeAura",
 *     "target": "FRIENDLY_MINIONS",
 *     "attribute": "AURA_CHARGE",
 *     "filter": {
 *       "class": "RaceFilter",
 *       "race": "BEAST"
 *     }
 *   }
 * </pre>
 */
public class AttributeAura extends Aura {

	public AttributeAura(AuraDesc desc) {
		super(desc.getSecondaryTrigger() != null ? desc.getSecondaryTrigger().create() : new WillEndSequenceTrigger(),
				AddAttributeSpell.create(desc.getAttribute()),
				RemoveAttributeSpell.create(desc.getAttribute()),
				desc.getTarget(),
				(EntityFilter) desc.get(AuraArg.FILTER),
				desc.getCondition());
		setDesc(desc);
	}
}


