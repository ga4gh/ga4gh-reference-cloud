package org.ga4gh.refcloud.api.refget.sequence;

import java.util.Optional;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsChecksumType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefgetSequenceRepository extends JpaRepository<RefgetSequence, String> {

    // a checksum is expected to match zero or one refget sequences; multiple
    // matches throw IncorrectResultSizeDataAccessException
    @Query("""
        SELECT DISTINCT rs FROM RefgetSequence rs
        JOIN FETCH rs.drsObject d
        JOIN d.checksums c
        WHERE c.type = :type AND c.checksum = :checksum
        """)
    Optional<RefgetSequence> findSequenceByChecksum(
        @Param("type") DrsChecksumType type,
        @Param("checksum") String checksum
    );

    // same lookup as findSequenceByChecksum, but also fetches the refget aliases
    // needed for the metadata response
    @Query("""
        SELECT DISTINCT rs FROM RefgetSequence rs
        JOIN FETCH rs.drsObject d
        LEFT JOIN FETCH rs.aliases
        JOIN d.checksums c
        WHERE c.type = :type AND c.checksum = :checksum
        """)
    Optional<RefgetSequence> findSequenceWithMetadataByChecksum(
        @Param("type") DrsChecksumType type,
        @Param("checksum") String checksum
    );
}
