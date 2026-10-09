package myau.events;

import myau.event.events.callables.EventCancellable;

/** Fired at the head of Minecraft.clickMouse (a left click / attack). Cancel it to swallow the click. */
public class LeftClickMouseEvent extends EventCancellable {
    public LeftClickMouseEvent() {
    }
}
