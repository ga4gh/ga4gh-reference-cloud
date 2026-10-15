package org.ga4gh.refcloud.api.dataconnect.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataConnectTableRepository extends JpaRepository<DataConnectTable, String> {

    @Query("""
        SELECT t FROM DataConnectTable t
        JOIN FETCH t.tableType
        WHERE t.tableName = :tableName
        """)
    Optional<DataConnectTable> findByTableNameWithTableType(@Param("tableName") String tableName);

    // a table is visible if it is platform-wide (no dataset), belongs to an open dataset
    // (no visa), or the user holds an approved assertion for the dataset's visa.
    // a null userId (anonymous request) only matches platform-wide and open tables
    @Query("""
        SELECT t FROM DataConnectTable t
        JOIN FETCH t.tableType
        LEFT JOIN t.dataset d
        LEFT JOIN d.passportVisa v
        WHERE t.dataset IS NULL
           OR v IS NULL
           OR EXISTS (
               SELECT 1 FROM PassportUserVisaAssertion a
               WHERE a.passportVisa = v
                 AND a.id.passportUserId = :userId
                 AND a.currentStatus = org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus.Approved
           )
        ORDER BY t.tableName
        """)
    List<DataConnectTable> findAllVisibleToUser(@Param("userId") String userId);
}
