package br.com.gestaofrota.api.v1.dtos.responses;
import java.util.*;
public record SvoOrderResourcesResponse(List<Option> drivers,List<Option> vehicles,List<Option> teams) {
    public record Option(UUID id,String label){}
}
