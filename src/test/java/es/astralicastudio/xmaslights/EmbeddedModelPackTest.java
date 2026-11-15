package es.astralicastudio.xmaslights;

import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.security.MessageDigest;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddedModelPackTest {
    private byte[] bytes, digest;
    private EmbeddedModelPack pack;
    private HttpClient client;
    @BeforeEach void start() throws Exception {
        try(var input=getClass().getResourceAsStream("/packs/"+ModelPackService.FILE_NAME)) { bytes=input.readAllBytes(); }
        digest=MessageDigest.getInstance("SHA-1").digest(bytes);
        pack=new EmbeddedModelPack("127.0.0.1",0,"",bytes,digest); client=HttpClient.newHttpClient();
    }
    @AfterEach void stop() { pack.close(); client.close(); }
    private HttpResponse<byte[]> request(String address,String method) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(address)).timeout(Duration.ofSeconds(5)).method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void exactZipIsServedWithItsSha1AndHeadDoesNotSendABody() throws Exception {
        var get=request(pack.url("127.0.0.1"),"GET"); assertEquals(200,get.statusCode()); assertArrayEquals(bytes,get.body());
        assertEquals("application/zip",get.headers().firstValue("Content-Type").orElseThrow());
        assertArrayEquals(digest,MessageDigest.getInstance("SHA-1").digest(get.body()));
        var head=request(pack.url("127.0.0.1"),"HEAD"); assertEquals(200,head.statusCode()); assertEquals(0,head.body().length);
        assertEquals(Integer.toString(bytes.length),head.headers().firstValue("Content-Length").orElseThrow());
    }
    @Test void noArbitraryPathsQueriesOrWriteMethodsAreExposed() throws Exception {
        String url=pack.url("127.0.0.1");
        assertEquals(404,request(url.replace(ModelPackService.FILE_NAME,"config.yml"),"GET").statusCode());
        assertEquals(404,request(url+"?file=../../config.yml","GET").statusCode());
        assertEquals(404,request(url.replace("/xmaslights/","/../xmaslights/"),"GET").statusCode());
        assertEquals(405,request(url,"POST").statusCode());
    }
    @Test void closingTheHostReleasesItsSocket() throws Exception {
        int port=pack.port(); pack.close();
        try(var socket=new java.net.ServerSocket()) { socket.setReuseAddress(true); socket.bind(new InetSocketAddress("127.0.0.1",port)); }
    }
    @Test void publicBaseAndIpv6AddressesProduceValidDownloadUrls() throws Exception {
        assertEquals("2001:db8::1",URI.create(pack.url("2001:db8::1")).getHost().replace("[","").replace("]",""));
        try(var hosted=new EmbeddedModelPack("127.0.0.1",0,"https://packs.example.test/proxy/",bytes,digest)) {
            assertTrue(hosted.url("ignored.example.test").startsWith("https://packs.example.test/proxy/xmaslights/"));
        }
        assertThrows(IllegalArgumentException.class,()->EmbeddedModelPack.validateBase("https://user:password@example.test"));
        assertThrows(IllegalArgumentException.class,()->EmbeddedModelPack.validateBase("https://example.test?token=secret"));
    }
}
