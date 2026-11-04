package org.ga4gh.refcloud.api.dataconnect.tabletype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataConnectTableTypeRepository extends JpaRepository<DataConnectTableType, String> {
}
