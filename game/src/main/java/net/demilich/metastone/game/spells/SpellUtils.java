package net.demilich.metastone.game.spells;

import co.paralleluniverse.fibers.Suspendable;
import net.demilich.metastone.game.actions.*;
import net.demilich.metastone.game.cards.desc.CardDesc;
import net.demilich.metastone.game.utils.Attribute;
import net.demilich.metastone.game.GameContext;
import net.demilich.metastone.game.Player;
import net.demilich.metastone.game.cards.*;
import net.demilich.metastone.game.entities.Actor;
import net.demilich.metastone.game.entities.Entity;
import net.demilich.metastone.game.entities.EntityType;
import net.demilich.metastone.game.entities.heroes.HeroClass;
import net.demilich.metastone.game.entities.minions.Minion;
import net.demilich.metastone.game.entities.minions.RelativeToSource;
import net.demilich.metastone.game.environment.Environment;
import net.demilich.metastone.game.spells.desc.SpellArg;
import net.demilich.metastone.game.spells.desc.SpellDesc;
import net.demilich.metastone.game.spells.desc.filter.EntityFilter;
import net.demilich.metastone.game.spells.desc.filter.Operation;
import net.demilich.metastone.game.targeting.EntityReference;
import net.demilich.metastone.game.targeting.TargetSelection;
import net.demilich.metastone.game.targeting.Zones;
import net.demilich.metastone.game.utils.AttributeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static net.demilich.metastone.game.spells.CastRandomSpellSpell.determineCastingPlayer;

/**
 * A set of utilities to help write spells.
 */
public class SpellUtils {
	private static Logger logger = LoggerFactory.getLogger(SpellUtils.class);

	/**
	 * Sets upu the source and target references for casting a child spell, typically an "effect" of a spell defined on a
	 * card.
	 *
	 * @param context The game context.
	 * @param player  The player casting the spell.
	 * @param spell   The child spell to cast.
	 * @param source  The source of the spell, typically the spell card or minion whose battlecry is being called.
	 * @param target  The target reference.
	 */
	@Suspendable
	public static void castChildSpell(GameContext context, Player player, SpellDesc spell, Entity source, Entity target) {
		EntityReference sourceReference = source != null ? source.getReference() : null;
		EntityReference targetReference = spell.getTarget();
		// Inherit target
		if (targetReference == null && target != null) {
			targetReference = target.getReference();
		}
		if (sourceReference == null) {
			sourceReference = EntityReference.NONE;
		}
		if (targetReference == null) {
			targetReference = EntityReference.NONE;
		}
		context.getLogic().castSpell(player.getId(), spell, sourceReference, targetReference, true);
	}

	/**
	 * Plays a card "randomly."
	 *
	 * @param context
	 * @param player
	 * @param card
	 * @param source
	 * @param summonRightmost       When {@code true} and {@link Card#isActor()}, summons the card in the rightmost
	 *                              position. Otherwise, summons it to a random position.
	 * @param resolveBattlecry      When {@code true}, also resolves the battlecry with a random target. Otherwise, the
	 *                              battlecry is ignored.
	 * @param onlyWhileSourceInPlay When {@code true},
	 * @param randomChooseOnes      When {@code true}, randomly chooses the choose-one effects. Otherwise, checks if the
	 *                              card has had a {@link Attribute#CHOICES} made.
	 * @param playFromHand          When {@code true}, the card is counterable, mana is deducted and played card stats are
	 *                              incremented. Otherwise, only the effects of the card, like putting a minion into play
	 *                              or the underlying spell effects, are executed.
	 * @return
	 */
	@Suspendable
	public static boolean playCardRandomly(GameContext context,
	                                       Player player,
	                                       Card card,
	                                       Entity source,
	                                       boolean summonRightmost,
	                                       boolean resolveBattlecry,
	                                       boolean onlyWhileSourceInPlay,
	                                       boolean randomChooseOnes,
	                                       boolean playFromHand) {

		CastRandomSpellSpell.DetermineCastingPlayer determineCastingPlayer = determineCastingPlayer(context, player, source, TargetPlayer.SELF);
		// Stop casting battlecries if Shudderwock is transformed or destroyed
		if (onlyWhileSourceInPlay && !determineCastingPlayer.isSourceInPlay()) {
			return false;
		}

		Player castingPlayer = determineCastingPlayer.getCastingPlayer();

		player.getAttributes().put(Attribute.RANDOM_CHOICES, true);

		PlayCardAction action = null;
		if (card.isChooseOne()) {
			if (context.getLogic().hasAttribute(player, Attribute.BOTH_CHOOSE_ONE_OPTIONS)) {
				action = card.playBothOptions();
			} else {
				boolean doesNotContainChoice = !card.getAttributes().containsKey(Attribute.CHOICE)
						&& card.getAttributes().containsKey(Attribute.PLAYED_FROM_HAND_OR_DECK);
				if (randomChooseOnes || doesNotContainChoice) {
					if (doesNotContainChoice) {
						logger.warn("playCardRandomly {} {}: A choose one card {} played from the hand does not contain a choice. Choosing randomly.",
								context.getGameId(), source, card);
					}
					PlayCardAction[] options = card.playOptions();
					action = options[context.getLogic().random(options.length)];
				} else if (card.getAttributes().containsKey(Attribute.CHOICE)) {
					action = card.playOptions()[card.getAttributeValue(Attribute.CHOICE)];
				}
			}
		} else {
			action = card.play();
		}

		if (action == null) {
			logger.error("playCardRandom {} {}: No action generated for card {}", context.getGameId(), source, card);
			return false;
		}

		action = action.clone();

		if (card.isActor()) {
			if (!summonRightmost && card.getCardType() == CardType.MINION) {
				int minionCount = player.getMinions().size();
				int targetIndex = context.getLogic().random(minionCount + 1);
				if (targetIndex == minionCount) {
					// Summon at the rightmost spot (default)
				} else {
					// summon next to the specified target
					action.setTargetReference(player.getMinions().get(targetIndex).getReference());
				}
			}

			HasBattlecry actionWithBattlecry = ((HasBattlecry) action);
			// Do we resolve battlecries?
			if (resolveBattlecry) {
				// The action either already has a battlecry specified because it was a choose one, or we have to retrieve
				// the action from the actor that would be summoned.
				BattlecryAction specifiedAction;
				if (actionWithBattlecry.getBattlecryAction() == null) {
					// Look at the actor
					Actor actor = card.actor();
					if (actor == null) {
						logger.error("playCardRandom {} {}: The actor is missing from the card {}", context.getGameId(), source, card);
						return false;
					}
					specifiedAction = actor.getBattlecry();
				} else {
					specifiedAction = actionWithBattlecry.getBattlecryAction();
				}

				if (specifiedAction != null) {
					// We found a battlecry
					specifiedAction = specifiedAction.clone();

					// If a target is required then we'll see if there are valid targets and execute it. If a target isn't required,
					// then the battlecry will do what it needs to do.
					if (specifiedAction.getTargetRequirement() != null
							&& specifiedAction.getTargetRequirement() != TargetSelection.NONE) {
						List<Entity> targets = context.getLogic().getValidTargets(castingPlayer.getId(), action);
						if (targets.isEmpty()) {
							// Don't execute the battlecry if there are no valid targets for one that requires targets but still
							// put the actor into play
							specifiedAction = BattlecryAction.NONE;
						} else {
							EntityReference battlecryTarget = context.getLogic().getRandom(targets).getReference();
							specifiedAction.setTargetReference(battlecryTarget);
						}
						actionWithBattlecry.setBattlecryAction(specifiedAction);
					}
				}
			} else {
				// No matter what the battlecry, clear it. This way, when the action is executed, resolve battlecry can be
				// true but this method's parameter to not resolve battlecries will be respected
				actionWithBattlecry.setBattlecryAction(BattlecryAction.NONE);
			}
		} else if (card.isSpell() || card.isHeroPower()) {
			// This is some other kind of action that takes a target
			if (action.getTargetRequirement() != null && action.getTargetRequirement() != TargetSelection.NONE) {
				List<Entity> targets = context.getLogic().getValidTargets(player.getId(), action);
				EntityReference randomTarget = null;
				if (targets != null && !targets.isEmpty()) {
					randomTarget = context.getLogic().getRandom(targets).getReference();
					action.setTargetReference(randomTarget);
				} else {
					// Card should be revealed, but there were no valid targets so the spell isn't cast
					// TODO: It's not obvious if cards with no valid targets should be uncastable if their conditions permit it
					return true;
				}
			}

			// Target requirement may have been none, but the action is still valid.
		} else {
			logger.error("playCardRandomly {} {}: Unsupported card type {} for card {}", context.getGameId(), source, card.getCardType(), card);
			return false;
		}

		// Do the deed
		if (playFromHand) {
			action.execute(context, player.getId());
		} else {
			action.innerExecute(context, player.getId());
		}

		player.getAttributes().remove(Attribute.RANDOM_CHOICES);
		return true;
	}


	/**
	 * Given a filter {@link Operation}, return a boolean representing whether that operation is satisfied.
	 *
	 * @param operation   The algebraic operation.
	 * @param actualValue The left hand side.
	 * @param targetValue The right hand side.
	 * @return {@code true} if the evaluation is truue.
	 */
	public static boolean evaluateOperation(Operation operation, int actualValue, int targetValue) {
		switch (operation) {
			case EQUAL:
				return actualValue == targetValue;
			case GREATER:
				return actualValue > targetValue;
			case GREATER_OR_EQUAL:
				return actualValue >= targetValue;
			case HAS:
				return actualValue > 0;
			case LESS:
				return actualValue < targetValue;
			case LESS_OR_EQUAL:
				return actualValue <= targetValue;
		}
		return false;
	}

	/**
	 * Filters a card list. Does not copy source cards.
	 *
	 * @param source A {@link CardList} source.
	 * @param filter A function that returns {@code true} when the card should be kept, or {@code null} to include all
	 *               cards.
	 * @return A {@link CardList} backed by a mutable, non-copy {@link CardArrayList}.
	 */
	public static CardList getCards(CardList source, Predicate<Card> filter) {
		CardList result = new CardArrayList();
		for (Card card : source) {
			if (filter == null || filter.test(card)) {
				result.addCard(card);
			}
		}
		return result;
	}

	/**
	 * Gets a card out of a {@link SpellDesc}. Typically only consults the {@link SpellArg#CARD} property.
	 *
	 * @param context The context.
	 * @param spell   The {@link SpellDesc}.
	 * @return A card.
	 */
	public static Card getCard(GameContext context, SpellDesc spell) {
		String cardId = (String) spell.get(SpellArg.CARD);
		return getSingleCard(context, cardId);
	}

	/**
	 * Consider the {@link Environment#PENDING_CARD} and {@link Environment#OUTPUTS}, and the {@link Zones#DISCOVER} zone
	 * for the specified card
	 *
	 * @param context
	 * @param cardId
	 * @return
	 */
	private static Card getSingleCard(GameContext context, String cardId) {
		if (cardId == null) {
			return null;
		}
		Card card;
		if (cardId.toUpperCase().equals("EVENT_SOURCE")) {
			card = (Card)context.resolveSingleTarget(context.getEventSourceStack().peek());
		} else if (cardId.toUpperCase().equals("OUTPUT")) {
			card = context.getOutputCard();
		} else {
			card = getCardFromContextOrDiscover(context, cardId);
		}
		return card;
	}

	/**
	 * Retrieves the cards specified inside the {@link SpellArg#CARD} and {@link SpellArg#CARDS} arguments.
	 *
	 * @param context The game context to use for {@link GameContext#getPendingCard()} or {@link
	 *                GameContext#getOutputCard()} lookups.
	 * @param spell   The spell description to retrieve the cards from.
	 * @return A new array of {@link Card} entities.
	 * @see #castChildSpell(GameContext, Player, SpellDesc, Entity, Entity, Entity) for a description of what an {@code
	 * "OUTPUT_CARD"} value corresponds to.
	 */
	public static Card[] getCards(GameContext context, SpellDesc spell) {
		String[] cardIds;
		if (spell.containsKey(SpellArg.CARDS)) {
			cardIds = (String[]) spell.get(SpellArg.CARDS);
		} else if (spell.containsKey(SpellArg.CARD)) {
			cardIds = new String[1];
			cardIds[0] = (String) spell.get(SpellArg.CARD);
		} else {
			return new Card[0];
		}
		Card[] cards = new Card[cardIds.length];
		for (int i = 0; i < cards.length; i++) {
			// If the discover zone contains the card, reference it instead
			final String cardId = cardIds[i];
			cards[i] = getSingleCard(context, cardId);
		}
		return cards;
	}

	public static Card getCardFromContextOrDiscover(GameContext context, String cardId) {
		return context.getPlayers().stream()
				.flatMap(p -> p.getDiscoverZone().stream())
				.filter(c -> c.getCardId().equals(cardId))
				.findFirst()
				.orElseGet(() -> context.getCardById(cardId));
	}

	/**
	 * Requests that the player chooses from a selection of cards and casts a spell (typically {@link ReceiveCardSpell}
	 * with that card.
	 * <p>
	 * This method makes a network request if required.
	 *
	 * @param context The game context that hosts the player and state for this request.
	 * @param player  The player that will choose from the cards.
	 * @param desc    For every card the player can discover, this method will create a {@link Spell} from this {@link
	 *                SpellDesc} and set its {@link SpellArg#CARD} argument to the discoverable card. Typically, this
	 *                {@link SpellDesc} defines a {@link ReceiveCardSpell}, {@link ReceiveCardAndDoSomethingSpell}, or a
	 *                {@link ChangeHeroPowerSpell}. These spells all receive cards as arguments. This argument allows a
	 *                {@link DiscoverAction} to do more sophisticated things than just put cards into hands.
	 * @param cards   A {@link CardList} of cards that get copied, added to the {@link Zones#DISCOVER} zone of the player
	 *                and shown in the discover card UI to the player.
	 * @return The {@link DiscoverAction} that corresponds to the card the player chose.
	 * @see DiscoverCardSpell for the spell that typically calls this method.
	 * @see ReceiveCardSpell for the spell that is typically the {@link SpellArg#SPELL} property of a {@link
	 * DiscoverCardSpell}.
	 */
	@Suspendable
	public static DiscoverAction discoverCard(GameContext context, Player player, SpellDesc desc, CardList cards) {
		// Discovers always work with a copy of the incoming cards
		cards = cards.getCopy();
		SpellDesc spell = (SpellDesc) desc.get(SpellArg.SPELL);
		List<GameAction> discoverActions = new ArrayList<>();
		for (int i = 0; i < cards.getCount(); i++) {
			Card card = cards.get(i);
			card.setId(context.getLogic().generateId());
			card.setOwner(player.getId());
			card.moveOrAddTo(context, Zones.DISCOVER);

			SpellDesc spellClone = spell.addArg(SpellArg.CARD, card.getCardId());
			DiscoverAction discover = DiscoverAction.createDiscover(spellClone);
			discover.setCard(card);
			discover.setId(i);
			discoverActions.add(discover);
		}

		return postDiscover(context, player, cards, discoverActions);
	}

	@Suspendable
	public static DiscoverAction postDiscover(GameContext context, Player player, Iterable<? extends Card> cards, List<GameAction> discoverActions) {
		if (discoverActions.size() == 0) {
			return null;
		}
		final DiscoverAction discoverAction;

		if (context.getLogic().attributeExists(Attribute.RANDOM_CHOICES)) {
			discoverAction = (DiscoverAction) context.getLogic().getRandom(discoverActions);
		} else {
			discoverAction = (DiscoverAction) context.getLogic().requestAction(player, discoverActions);
		}

		// We do not perform the game action here

		// Move the cards back
		for (Card card : cards) {
			// Cards that are being discovered are always copies, so they are always removed from play afterwards.
			if (card.getZone() == Zones.DISCOVER) {
				card.moveOrAddTo(context, Zones.REMOVED_FROM_PLAY);
			}

			context.getLogic().removeCard(card);
		}

		return discoverAction;
	}

	/**
	 * Requests that the player chooses from a selection of cards, then returns just the spell from the cards.
	 * <p>
	 * Removes all the cards from play unless otherwise specified, since these cards aren't actually used.
	 *
	 * @param context The {@link GameContext}
	 * @param player  The {@link Player}
	 * @param desc    A {@link SpellDesc} to use as the "parent" of the discovered spells. The mana cost and targets are
	 *                inherited from this spell.
	 * @param spells  A list of spells from which to generate virtual cards.
	 * @param source  The source entity, typically the {@link Card} or {@link Minion#getBattlecry()} that initiated this
	 *                call.
	 * @return A {@link DiscoverAction} whose {@link DiscoverAction#getCard()} property corresponds to the selected card.
	 * To retrieve the spell, get the card's spell with {@link Card#getSpell()}.
	 */
	@Suspendable
	public static DiscoverAction getSpellDiscover(GameContext context, Player player, SpellDesc desc, List<SpellDesc> spells, Entity source) {
		List<GameAction> discoverActions = new ArrayList<>();
		List<Card> cards = new ArrayList<>();
		for (int i = 0; i < spells.size(); i++) {
			final SpellDesc spell = spells.get(i);
			final CardDesc spellCardDesc = new CardDesc();
			final String name = spell.getString(SpellArg.NAME);
			final String description = spell.getString(SpellArg.DESCRIPTION);
			// TODO: Parse the parenthesized part of a name in a spell as a description
			spellCardDesc.setId(context.getLogic().generateCardId());
			spellCardDesc.setName(name);
			List<Entity> entities = context.resolveTarget(player, source, desc.getTarget());
			int baseManaCost = 0;
			if (entities != null
					&& entities.size() > 0) {
				baseManaCost = desc.getValue(SpellArg.MANA, context, player, entities.get(0), source, 0);
			}
			spellCardDesc.setBaseManaCost(baseManaCost);
			spellCardDesc.setDescription(description);
			spellCardDesc.setHeroClass(HeroClass.ANY);
			spellCardDesc.setType(CardType.SPELL);
			spellCardDesc.setRarity(Rarity.FREE);
			spellCardDesc.setTargetSelection((TargetSelection) spell.getOrDefault(SpellArg.TARGET_SELECTION, desc.getOrDefault(SpellArg.TARGET_SELECTION, TargetSelection.NONE)));
			spellCardDesc.setSpell(spell);
			spellCardDesc.setCollectible(false);

			Card card = spellCardDesc.create();
			card.setId(context.getLogic().generateId());
			card.setOwner(player.getId());
			card.moveOrAddTo(context, Zones.DISCOVER);
			cards.add(card);

			DiscoverAction discover = DiscoverAction.createDiscover(spell);
			discover.setId(i);
			discover.setCard(card);
			discoverActions.add(discover);
		}

		return postDiscover(context, player, cards, discoverActions);
	}

	public static List<Actor> getValidRandomTargets(List<Entity> targets) {
		List<Actor> validTargets = new ArrayList<Actor>();
		for (Entity entity : targets) {
			Actor actor = (Actor) entity;
			if (!actor.isDestroyed() || actor.getEntityType() == EntityType.HERO) {
				validTargets.add(actor);
			}

		}
		return validTargets;
	}

	public static List<Entity> getValidTargets(GameContext context, Player player, List<Entity> allTargets, EntityFilter filter) {
		if (filter == null) {
			return allTargets;
		}
		List<Entity> validTargets = new ArrayList<>();
		for (Entity entity : allTargets) {
			if (filter.matches(context, player, entity, null)) {
				validTargets.add(entity);
			}
		}
		return validTargets;
	}

	public static boolean highlanderDeck(Player player) {
		return player.getDeck().stream().map(Card::getCardId).distinct().count() == player.getDeck().getCount();
	}

	@Suspendable
	public static int howManyMinionsDiedThisTurn(GameContext context) {
		int currentTurn = context.getTurn();
		int count = 0;
		for (Player player : context.getPlayers()) {
			for (Entity deadEntity : player.getGraveyard()) {
				if (deadEntity.getEntityType() != EntityType.MINION) {
					continue;
				}

				if (deadEntity.getAttributeValue(Attribute.DIED_ON_TURN) == currentTurn) {
					count++;
				}

			}
		}
		return count;
	}

	public static int getBoardPosition(GameContext context, Player player, SpellDesc desc, Entity source) {
		final int UNDEFINED = -1;
		int boardPosition = desc.getValue(SpellArg.BOARD_POSITION_ABSOLUTE, context, player, null, source, UNDEFINED);
		if (boardPosition != UNDEFINED) {
			return boardPosition;
		}
		RelativeToSource relativeBoardPosition = (RelativeToSource) desc.get(SpellArg.BOARD_POSITION_RELATIVE);
		if (relativeBoardPosition == null) {
			return UNDEFINED;
		}

		int sourcePosition = ((Minion) source).getEntityLocation().getIndex();
		if (sourcePosition == UNDEFINED) {
			return UNDEFINED;
		}
		switch (relativeBoardPosition) {
			case LEFT:
				return sourcePosition;
			case RIGHT:
				return sourcePosition + 1;
			default:
				return UNDEFINED;
		}
	}

	static SpellDesc[] getGroup(GameContext context, SpellDesc spell) {
		Card card = null;
		String cardName = (String) spell.get(SpellArg.GROUP);
		card = context.getCardById(cardName);
		if (card.getCardType() == CardType.GROUP) {
			return card.getGroup();
		}
		return null;
	}

	static AttributeMap processKeptEnchantments(Entity target, AttributeMap map) {
		if (target.hasAttribute(Attribute.KEEPS_ENCHANTMENTS)) {
			Stream.of(Attribute.POISONOUS, Attribute.LIFESTEAL, Attribute.WINDFURY, Attribute.ATTACK_BONUS, Attribute.HP_BONUS)
					.filter(target::hasAttribute).forEach(k -> map.put(k, target.getAttributes().get(k)));
		}
		return map;
	}

	/**
	 * Casts a subspell on a card that was returned by {@link net.demilich.metastone.game.logic.GameLogic#receiveCard(int,
	 * Card)}. Will not execute if the output is null or in the {@link Zones#GRAVEYARD}.
	 *
	 * @param context The {@link GameContext} to operate on.
	 * @param player  The player from whose point of view we are casting this sub spell. This should be passed down from
	 *                the {@link Spell#onCast(GameContext, Player, SpellDesc, Entity, Entity)} {@code player} argument.
	 * @param spell   The sub spell, typically from the {@code desc} argument's {@link SpellArg#SPELL} key.
	 * @param source  The source entity.
	 * @param target
	 * @param output  The card. When {@code null} or the card is located in the {@link Zones#GRAVEYARD}.
	 */
	@Suspendable
	public static void castChildSpell(GameContext context, Player player, SpellDesc spell, Entity source, Entity target, Entity output) {
		// card may be null (i.e. try to draw from deck, but already in
		// fatigue)
		if (output == null
				|| output.getZone() == Zones.GRAVEYARD) {
			return;
		}
		if (spell == null) {
			return;
		}

		context.getOutputStack().push(output.getReference());
		castChildSpell(context, player, spell, source, target);
		context.getOutputStack().pop();
	}

	/**
	 * Retrieves the cards specified in the {@link SpellDesc}, either in the {@link SpellArg#CARD} or {@link
	 * SpellArg#CARDS} properties or as specified by a {@link net.demilich.metastone.game.spells.desc.source.CardSource}
	 * and {@link net.demilich.metastone.game.spells.desc.filter.CardFilter}. If neither of those are specified, uses the
	 * target's {@link Entity#getSourceCard()} as the targeted card.
	 * <p>
	 * The number of cards randomly retrieved is equal to the {@link SpellArg#VALUE} specified in the {@code desc}
	 * argument, defaulting to 1.
	 *
	 * @param context The game context
	 * @param player  The player from whose point of view these cards should be retrieved
	 * @param target  The target, which can be {@code null}
	 * @param source  The source or host {@link Entity}, typically the origin of this spell cast.
	 * @param desc    The {@link SpellDesc} typically of the calling spell.
	 * @return A list of cards.
	 */
	public static CardList getCards(GameContext context, Player player, Entity target, Entity source, SpellDesc desc) {
		return getCards(context, player, target, source, desc, desc.getValue(SpellArg.VALUE, context, player, target, source, 1));
	}

	/**
	 * Retrieves the cards specified in the {@link SpellDesc}, either in the {@link SpellArg#CARD} or {@link
	 * SpellArg#CARDS} properties or as specified by a {@link net.demilich.metastone.game.spells.desc.source.CardSource}
	 * and {@link net.demilich.metastone.game.spells.desc.filter.CardFilter}. If neither of those are specified, uses the
	 * target's {@link Entity#getSourceCard()} as the targeted card.
	 *
	 * @param context The game context
	 * @param player  The player from whose point of view these cards should be retrieved
	 * @param target  The target, which can be {@code null}
	 * @param source  The source or host {@link Entity}, typically the origin of this spell cast.
	 * @param desc    The {@link SpellDesc} typically of the calling spell.
	 * @param count   The maximum number of cards to return, exclusively and randomly, from the generated card list. Or,
	 *                returns all the cards if {@code count > cards.size()}, where {@code cards} is all the possible
	 *                cards.
	 * @return A list of cards.
	 */
	public static CardList getCards(GameContext context, Player player, Entity target, Entity source, SpellDesc desc, int count) {
		CardList cards = new CardArrayList(Arrays.asList(getCards(context, desc)));
		boolean hasCardSourceOrFilter = desc.containsKey(SpellArg.CARD_SOURCE) || desc.containsKey(SpellArg.CARD_FILTER);
		if (cards.isEmpty()) {
			if (hasCardSourceOrFilter) {
				cards.addAll(desc.getFilteredCards(context, player, source));
			} else if (target != null) {
				cards.add(target.getSourceCard());
			}
		}

		if (count < cards.size()) {
			CardList result = new CardArrayList();
			int i = count;
			while (cards.size() > 0
					&& i > 0) {
				result.add(context.getLogic().removeRandom(cards));
				i--;
			}
			return result;
		} else {
			return cards;
		}
	}
}
