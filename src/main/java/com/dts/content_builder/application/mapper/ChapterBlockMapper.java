package com.dts.content_builder.application.mapper;

import com.dts.content_builder.api.response.ChapterBlockDetailResponse;
import com.dts.content_builder.api.response.ChapterBlockTreeResponse;
import com.dts.content_builder.domain.entity.ChapterBlockEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ChapterBlockMapper {

    public ChapterBlockDetailResponse toDetailResponse(ChapterBlockEntity entity) {
        if (entity == null) {
            return null;
        }
        ChapterBlockDetailResponse response = new ChapterBlockDetailResponse();
        response.setId(entity.getId());
        response.setLearningProgramId(entity.getLearningProgramId());
        response.setParentId(entity.getParentId());
        response.setChapterId(entity.getChapterId());
        response.setTitle(entity.getTitle());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setMetadata(entity.getMetadata());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public List<ChapterBlockTreeResponse> toTreeResponse(List<ChapterBlockEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, ChapterBlockTreeResponse> nodeMap = entities.stream()
                .collect(Collectors.toMap(ChapterBlockEntity::getId, this::toTreeNode));

        List<ChapterBlockTreeResponse> roots = new ArrayList<>();

        for (ChapterBlockEntity entity : entities) {
            ChapterBlockTreeResponse node = nodeMap.get(entity.getId());
            if (entity.getParentId() == null) {
                roots.add(node);
            } else {
                ChapterBlockTreeResponse parent = nodeMap.get(entity.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }

        sortTree(roots);
        return roots;
    }

    private ChapterBlockTreeResponse toTreeNode(ChapterBlockEntity entity) {
        ChapterBlockTreeResponse response = new ChapterBlockTreeResponse();
        response.setId(entity.getId());
        response.setLearningProgramId(entity.getLearningProgramId());
        response.setParentId(entity.getParentId());
        response.setChapterId(entity.getChapterId());
        response.setTitle(entity.getTitle());
        response.setSortOrder(entity.getSortOrder());
        response.setStatus(entity.getStatus());
        response.setMetadata(entity.getMetadata());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setChildren(new ArrayList<>());
        return response;
    }

    private void sortTree(List<ChapterBlockTreeResponse> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(ChapterBlockTreeResponse::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        for (ChapterBlockTreeResponse node : nodes) {
            sortTree(node.getChildren());
        }
    }
}
