package org.metalib.net.url.jersey;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class CharacterIteratorTest {

    @Test
    void callingIteratorNextOnLastElementShouldThrow() {
        final var iterator = new CharacterIterator("");
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void callingIteratorPeekOnLastElementShouldThrow() {
        final var iterator = new CharacterIterator("");
        assertThrows(NoSuchElementException.class, iterator::peek);
    }

    @Test
    void callingIteratorOnSetOutOfRangePositionShouldThrow() {
        final var iterator = new CharacterIterator("");
        assertThrows(IndexOutOfBoundsException.class, () -> iterator.setPosition(2));
    }

    @Test
    void callingIteratorOnUnsetCurrentPositionShouldThrow() {
        final var iterator = new CharacterIterator("");
        assertThrows(IllegalStateException.class, iterator::current);
    }

}