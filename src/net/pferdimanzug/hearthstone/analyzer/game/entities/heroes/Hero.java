package net.pferdimanzug.hearthstone.analyzer.game.entities.heroes;

import net.pferdimanzug.hearthstone.analyzer.game.GameTag;
import net.pferdimanzug.hearthstone.analyzer.game.entities.Entity;
import net.pferdimanzug.hearthstone.analyzer.game.entities.EntityType;
import net.pferdimanzug.hearthstone.analyzer.game.entities.weapons.Weapon;
import net.pferdimanzug.hearthstone.analyzer.game.heroes.powers.HeroPower;

public abstract class Hero extends Entity {

	private final HeroClass heroClass;
	private HeroPower heroPower;
	private Weapon weapon;
	
	public Hero(String name, HeroClass heroClass, HeroPower heroPower) {
		super(null);
		setName(name);
		this.heroClass = heroClass;
		this.heroPower = heroPower;
	}

	@Override
	public Hero clone() {
		Hero clone = (Hero) super.clone();
		if (weapon != null) {
			clone.setWeapon(getWeapon().clone());
		}
		clone.heroPower = heroPower.clone();
		return clone;
	}

	public int getArmor() {
		return getTagValue(GameTag.ARMOR);
	}
	
	@Override
	public int getAttack() {
		int attack = super.getAttack();
		if (getWeapon() != null) {
			attack += weapon.getAttack();
		}
		return attack;
	}
	
	@Override
	public EntityType getEntityType() {
		return EntityType.HERO;
	}
	
	public HeroClass getHeroClass() {
		return heroClass;
	}

	public HeroPower getHeroPower() {
		return heroPower;
	}

	public Weapon getWeapon() {
		return weapon;
	}

	public void modifyArmor(int armor) {
		// armor cannot fall below zero
		int newArmor = Math.max(getArmor() + armor, 0);
		setTag(GameTag.ARMOR, newArmor);
	}
	
	public void setWeapon(Weapon weapon) {
		this.weapon = weapon;
		if (weapon != null) {
			weapon.setOwner(getOwner());
		}
	}
	
}