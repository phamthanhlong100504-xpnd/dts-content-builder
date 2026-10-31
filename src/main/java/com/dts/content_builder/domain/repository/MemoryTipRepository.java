
package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.MemoryTipEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemoryTipRepository
        extends JpaRepository<MemoryTipEntity, UUID>,
                JpaSpecificationExecutor<MemoryTipEntity> {

    // Lay chi tiet mot meo, bo qua ban ghi da xoa mem
    Optional<MemoryTipEntity> findByIdAndDeletedAtIsNull(
            UUID id
    );

    // Tim meo theo ID nguon va phien ban, phuc vu import
    Optional<MemoryTipEntity> findBySourceIdAndBankVersionAndDeletedAtIsNull(
            Integer sourceId,
            String bankVersion
    );

    // Danh sach meo theo phien ban, ho tro phan trang
    Page<MemoryTipEntity> findByBankVersionAndDeletedAtIsNull(
            String bankVersion,
            Pageable pageable
    );

    // Dem so meo con hieu luc trong mot phien ban
    long countByBankVersionAndDeletedAtIsNull(
            String bankVersion
    );
}
