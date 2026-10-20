package org.ga4gh.refcloud.api.dataconnect.table;

import java.util.List;
import java.util.Optional;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DataConnectTableRepository extends JpaRepository<DataConnectTable, String> {

    // a table is visible if it is platform-wide (no dataset), belongs to an open dataset
    // (no visa), or the user holds an approved assertion for the dataset's visa.
    // a null userId (anonymous request) only matches platform-wide and open tables.
    // shared by all visibility queries so the access rule is defined once
    String VISIBLE_TO_USER_FROM_WHERE = """
        FROM DataConnectTable t
        JOIN FETCH t.tableType
        LEFT JOIN t.dataset d
        LEFT JOIN d.passportVisa v
        WHERE (
            t.dataset IS NULL
            OR v IS NULL
            OR EXISTS (
                SELECT 1 FROM PassportUserVisaAssertion a
                WHERE a.passportVisa = v
                  AND a.id.passportUserId = :userId
                  AND a.currentStatus = :approvedStatus
            )
        )
        """;

    @Query("""
        SELECT t FROM DataConnectTable t
        JOIN FETCH t.tableType
        WHERE t.tableName = :tableName
        """)
    Optional<DataConnectTable> findByTableNameWithTableType(@Param("tableName") String tableName);

    default List<DataConnectTable> findAllVisibleToUser(String userId) {
        return findAllVisibleToUser(userId, PassportVisaAssertionStatus.Approved);
    }

    default Optional<DataConnectTable> findVisibleByTableName(String tableName, String userId) {
        return findVisibleByTableName(tableName, userId, PassportVisaAssertionStatus.Approved);
    }

    // the approved status is bound as a parameter rather than written as an enum literal: Hibernate
    // renders enum literals as a cast to a type name derived from the Java class, which does not
    // match the postgres enum (passport_visa_assertion_status)
    @Query("SELECT t " + VISIBLE_TO_USER_FROM_WHERE + " ORDER BY t.tableName")
    List<DataConnectTable> findAllVisibleToUser(
        @Param("userId") String userId,
        @Param("approvedStatus") PassportVisaAssertionStatus approvedStatus
    );

    @Query("SELECT t " + VISIBLE_TO_USER_FROM_WHERE + " AND t.tableName = :tableName")
    Optional<DataConnectTable> findVisibleByTableName(
        @Param("tableName") String tableName,
        @Param("userId") String userId,
        @Param("approvedStatus") PassportVisaAssertionStatus approvedStatus
    );
}
