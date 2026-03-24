package tech.lemnova.continuum.infra.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import tech.lemnova.continuum.domain.note.Note;

import java.util.List;

@Repository
public interface NoteRepository extends MongoRepository<Note, String> {
    List<Note> findByUserId(String userId);
    long countByUserId(String userId);
    void deleteByUserId(String userId);

    /**
     * Busca apenas os campos necessários para construir o grafo (id, title, entityIds).
     * Evita carregar o content que pode ser grande, economizando memória e banda.
     *
     * @param userId ID do usuário
     * @return Lista de notas com apenas os campos essenciais para o grafo
     */
    @Query(value = "{ 'userId': ?0 }", fields = "{ 'id': 1, 'title': 1, 'entityIds': 1 }")
    List<Note> findGraphDataByUserId(String userId);
}
