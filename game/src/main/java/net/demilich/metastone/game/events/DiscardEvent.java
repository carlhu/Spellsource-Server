package net.demilich.metastone.game.events;

import net.demilich.metastone.game.GameContext;
import net.demilich.metastone.game.cards.Card;
import net.demilich.metastone.game.entities.Entity;

public class DiscardEvent extends GameEvent implements HasCard {
	private final Card card;

	public DiscardEvent(GameContext context, int playerId, Card card) {
		super(context, playerId, -1);
		this.card = card;
	}

	public Card getCard() {
		return card;
	}
	
	@Override
	public Entity getEventTarget() {
		return getCard();
	}

	@Override
	public GameEventType getEventType() {
		return GameEventType.DISCARD;
	}

	@Override
	public boolean isClientInterested() {
		return true;
	}
}
