package myau.events;

import myau.event.events.Event;

/**
 * Fired at the end of MovementInputFromOptions#updatePlayerMoveState, after vanilla has filled
 * in moveForward / moveStrafe / jump / sneak from the keybinds. Modules may edit
 * mc.thePlayer.movementInput here and the change is used for this tick's movement.
 */
public class MoveInputEvent implements Event {
}
