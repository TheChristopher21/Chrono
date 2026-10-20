package com.chrono.chrono.services.pms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
@Component
public class PmsBeds24Client {
    private final ObjectMapper mapper;private final PmsSecretResolver secrets;private final URI base;private final HttpClient http;
    private final Map<String,Token> tokens=new java.util.concurrent.ConcurrentHashMap<>();
    private record Token(String value,Instant expires){}
    public static class ProviderFailure extends RuntimeException {
        private final int retryAfterSeconds;
        ProviderFailure(String message,int retryAfterSeconds){super(message);this.retryAfterSeconds=retryAfterSeconds;}
        public int retryAfterSeconds(){return retryAfterSeconds;}
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PmsBeds24Client(ObjectMapper mapper,PmsSecretResolver secrets,@Value("${app.pms.beds24.base-url:https://beds24.com/api/v2/}") String base){
        this(mapper,secrets,validateBase(base),HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    PmsBeds24Client(ObjectMapper mapper,PmsSecretResolver secrets,URI base,HttpClient http){this.mapper=mapper;this.secrets=secrets;this.base=base;this.http=http;}
    static URI validateBase(String value){var uri=URI.create(value.endsWith("/")?value:value+"/");if(!"https".equals(uri.getScheme()) || !Set.of("beds24.com","api.beds24.com").contains(uri.getHost()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)throw new IllegalArgumentException("Beds24 benötigt einen offiziellen HTTPS-Endpunkt.");return uri;}
    private String token(String reference,boolean renew)throws Exception{
        String secret=secrets.resolve(reference).orElseThrow(() -> new ProviderFailure("Beds24-Refresh-Token ist auf dem Server nicht eingerichtet.",300));
        String key=reference+":"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        Token cached=tokens.get(key);if(!renew&&cached!=null&&cached.expires().isAfter(Instant.now()))return cached.value();
        JsonNode result=send(HttpRequest.newBuilder(base.resolve("authentication/token")).timeout(Duration.ofSeconds(20)).header("refreshToken",secret).GET().build());
        String value=result.path("token").asText();int expires=result.path("expiresIn").asInt();if(value.isBlank()||expires<1)throw new ProviderFailure("Beds24 lieferte kein gültiges Zugriffstoken.",300);
        tokens.put(key,new Token(value,Instant.now().plusSeconds(Math.max(1,expires-60))));return value;
    }
    private JsonNode call(String reference,String path,String method,JsonNode payload)throws Exception{
        String token=token(reference,false);
        for(int attempt=0;attempt<2;attempt++){
            var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20)).header("accept","application/json").header("token",token);
            if("POST".equals(method))request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));else request.GET();
            try{return send(request.build());}catch(ProviderFailure error){if(attempt==0&&error.getMessage().endsWith("HTTP 401.")){token=token(reference,true);continue;}throw error;}
        }
        throw new ProviderFailure("Beds24-Anmeldung fehlgeschlagen.",300);
    }
    private JsonNode send(HttpRequest request)throws Exception{
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=response.body()){
            int wait=Math.max(30,Math.min(3600,response.headers().firstValue("X-FiveMinCreditLimit-ResetsIn").map(v -> {try{return Integer.parseInt(v);}catch(NumberFormatException e){return 300;}}).orElse(300)));
            if(response.statusCode()<200||response.statusCode()>=300)throw new ProviderFailure("Beds24-Anfrage fehlgeschlagen: HTTP "+response.statusCode()+".",wait);
            byte[] bytes=stream.readNBytes(4_000_001);if(bytes.length>4_000_000)throw new ProviderFailure("Beds24-Antwort überschreitet die erlaubte Größe.",300);
            JsonNode result=mapper.readTree(bytes);if(result==null || result.isObject()&&result.has("success")&&!result.path("success").asBoolean())throw new ProviderFailure("Beds24 hat den Auftrag abgelehnt.",wait);return result;
        }
    }
    public Set<Long> verifyProperty(String reference,Long propertyId,String currencyCode)throws Exception{
        var result=call(reference,"properties?id="+propertyId+"&includeAllRooms=true","GET",null);
        JsonNode match=null;for(var p:result.path("data"))if(p.path("id").asLong()==propertyId)match=p;
        if(match==null)throw new ProviderFailure("Beds24-Hotel ist mit diesem Token nicht zugänglich.",300);
        String currency=match.path("currency").asText();if(currency.isBlank())currency=match.path("currencyCode").asText();
        if(!currencyCode.equalsIgnoreCase(currency))throw new ProviderFailure("Hotelwährung stimmt nicht mit Beds24 überein.",300);
        Set<Long> rooms=new HashSet<>();for(var room:match.has("roomTypes")?match.path("roomTypes"):match.path("rooms"))rooms.add(room.path("id").asLong());return rooms;
    }
    public void publishCalendar(String reference,JsonNode calendar)throws Exception{
        if(calendar==null||!calendar.isArray()||calendar.isEmpty())throw new IllegalArgumentException("Kalenderauftrag fehlt.");
        var result=call(reference,"inventory/rooms/calendar","POST",calendar);
        if(!result.isArray()||result.size()!=calendar.size())throw new ProviderFailure("Beds24 hat nicht jeden Kalenderteil bestätigt.",300);
        for(var item:result)if(!item.path("success").asBoolean(false))throw new ProviderFailure("Beds24 hat mindestens einen Kalenderteil abgelehnt; gleicher Auftrag bleibt wiederholbar.",300);
    }
    public record Booking(long id,long propertyId,long roomId,String status,String arrival,String departure,int adults,int children,java.math.BigDecimal price,String currency,String firstName,String lastName,String email){}
    public List<Booking> bookings(String reference,Long propertyId,java.time.LocalDate from,java.time.LocalDate to)throws Exception{
        List<Booking> bookings=new ArrayList<>();
        for(int page=1;page<=100;page++){
            var result=call(reference,"bookings?propertyId="+propertyId+"&departureFrom="+from.minusDays(1)+"&arrivalTo="+to.plusDays(1)+"&status=confirmed&status=new&status=cancelled&includeInvoiceItems=false&includeInfoItems=false&page="+page,"GET",null);
            if(!result.path("data").isArray())throw new ProviderFailure("Beds24-Buchungsantwort ist unvollständig.",300);
            for(var r:result.path("data")){
                if(r.path("propertyId").asLong()!=propertyId)throw new ProviderFailure("Beds24 lieferte eine Buchung eines anderen Hotels.",300);
                bookings.add(new Booking(r.path("id").asLong(),propertyId,r.path("roomId").asLong(),r.path("status").asText(),r.path("arrival").asText(),r.path("departure").asText(),r.path("numAdult").asInt(),r.path("numChild").asInt(),r.path("price").isNumber()?r.path("price").decimalValue():null,r.path("currency").asText(null),r.path("firstName").asText(),r.path("lastName").asText(),r.path("email").asText()));
            }
            if(bookings.size()>10000)throw new ProviderFailure("Abgleich bitte auf einen kleineren Zeitraum begrenzen.",300);
            if(!result.path("pages").path("nextPageExists").asBoolean())return bookings;
        }
        throw new ProviderFailure("Beds24-Abgleich hat die Seitengrenze erreicht.",300);
    }
}
