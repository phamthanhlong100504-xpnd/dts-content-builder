
package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.TrafficSignEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrafficSignRepository
        extends JpaRepository<TrafficSignEntity, UUID>,
                JpaSpecificationExecutor<TrafficSignEntity> {

    // Tim bien bao theo UUID, bo qua ban ghi da xoa mem
    Optional<TrafficSignEntity> findByIdAndDeletedAtIsNull(
            UUID id
    );

    // Tim theo ID nguon va phien ban, phuc vu import
    Optional<TrafficSignEntity> findBySourceIdAndBankVersionAndDeletedAtIsNull(
            Integer sourceId,
            String bankVersion
    );

    // Tim theo ma bien trong mot phien ban
    Optional<TrafficSignEntity> findByCodeIgnoreCaseAndBankVersionAndDeletedAtIsNull(
            String code,
            String bankVersion
    );

    // Lay danh sach theo phien ban va trang thai
    Page<TrafficSignEntity> findByBankVersionAndStatusAndDeletedAtIsNull(
            String bankVersion,
            TrafficSignEntity.Status status,
            Pageable pageable
    );

    // Dem so bien bao theo trang thai, phuc vu kiem tra publish
    long countByBankVersionAndStatusAndDeletedAtIsNull(
            String bankVersion,
            TrafficSignEntity.Status status
    );
}
