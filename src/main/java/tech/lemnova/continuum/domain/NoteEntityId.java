package tech.lemnova.continuum.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteEntityId implements Serializable {

    private String noteId;
    private String entityId;
}