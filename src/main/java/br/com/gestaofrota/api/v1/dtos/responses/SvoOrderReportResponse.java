package br.com.gestaofrota.api.v1.dtos.responses;
import java.util.List;
public record SvoOrderReportResponse(long totalOrders,long collectionCount,long deliveryCount,
        Double averageRequestToCollectionMinutes,Double averageCollectionToDeliveryMinutes,Double averageRequestToDeliveryMinutes,
        List<SvoOrderResponse> orders) {}
