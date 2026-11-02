package com.example.his.core;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service
public final class CryptoService {
 private final SecretKeySpec key;
 private final SecureRandom random=new SecureRandom();
 public CryptoService(@Value("${his.data-key:}")String hex){if(!hex.matches("[a-fA-F0-9]{64}"))throw new IllegalStateException("DATA_ENCRYPTION_KEY requires a 64-character hex key");key=new SecretKeySpec(HexFormat.of().parseHex(hex),"AES");}
 public String encrypt(String data){if(data==null||data.isEmpty())return "";try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));byte[] ciphertext=cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));byte[] together=new byte[nonce.length+ciphertext.length];System.arraycopy(nonce,0,together,0,nonce.length);System.arraycopy(ciphertext,0,together,nonce.length,ciphertext.length);return Base64.getEncoder().encodeToString(together);}catch(GeneralSecurityException e){throw new IllegalStateException("敏感数据加密失败",e);}}
 public String decrypt(Object data){if(data==null||String.valueOf(data).isEmpty())return "";try{byte[] b=Base64.getDecoder().decode(String.valueOf(data));if(b.length<29)throw new GeneralSecurityException("ciphertext short");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(b,0,12)));return new String(cipher.doFinal(Arrays.copyOfRange(b,12,b.length)),StandardCharsets.UTF_8);}catch(GeneralSecurityException e){throw new IllegalStateException("敏感数据无法解密，请检查密钥是否变更",e);}}
 public String fingerprint(String value){if(value==null||value.isBlank())return null;try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(key);return HexFormat.of().formatHex(mac.doFinal(value.trim().getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
}
