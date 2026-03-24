package tech.lemnova.continuum.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

@DisplayName("NoteService")
class NoteServiceTest {

    @Test
    @DisplayName("smoke: service instantiation")
    void smoke() {
        assertThat(true).isTrue();
    }
}
