package com.android.org.conscrypt;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactorySpi;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

public final class OpenSSLRSAKeyFactory extends KeyFactorySpi {
    protected PublicKey engineGeneratePublic(KeySpec keySpec) throws InvalidKeySpecException {
        if (keySpec == null) {
            throw new InvalidKeySpecException("keySpec == null");
        } else if (keySpec instanceof RSAPublicKeySpec) {
            return new OpenSSLRSAPublicKey((RSAPublicKeySpec) keySpec);
        } else {
            if (keySpec instanceof X509EncodedKeySpec) {
                return OpenSSLKey.getPublicKey((X509EncodedKeySpec) keySpec, 6);
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Must use RSAPublicKeySpec or X509EncodedKeySpec; was ");
            stringBuilder.append(keySpec.getClass().getName());
            throw new InvalidKeySpecException(stringBuilder.toString());
        }
    }

    protected PrivateKey engineGeneratePrivate(KeySpec keySpec) throws InvalidKeySpecException {
        if (keySpec == null) {
            throw new InvalidKeySpecException("keySpec == null");
        } else if (keySpec instanceof RSAPrivateCrtKeySpec) {
            return new OpenSSLRSAPrivateCrtKey((RSAPrivateCrtKeySpec) keySpec);
        } else {
            if (keySpec instanceof RSAPrivateKeySpec) {
                return new OpenSSLRSAPrivateKey((RSAPrivateKeySpec) keySpec);
            }
            if (keySpec instanceof PKCS8EncodedKeySpec) {
                return OpenSSLKey.getPrivateKey((PKCS8EncodedKeySpec) keySpec, 6);
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Must use RSAPublicKeySpec or PKCS8EncodedKeySpec; was ");
            stringBuilder.append(keySpec.getClass().getName());
            throw new InvalidKeySpecException(stringBuilder.toString());
        }
    }

    protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException {
        byte[] encoded;
        RSAPrivateCrtKey rsaKey;
        RSAPrivateKey privKey;
        StringBuilder stringBuilder;
        if (key == null) {
            throw new InvalidKeySpecException("key == null");
        } else if (keySpec == null) {
            throw new InvalidKeySpecException("keySpec == null");
        } else if (!"RSA".equals(key.getAlgorithm())) {
            throw new InvalidKeySpecException("Key must be a RSA key");
        } else if ((key instanceof RSAPublicKey) && RSAPublicKeySpec.class.isAssignableFrom(keySpec)) {
            RSAPublicKey rsaKey2 = (RSAPublicKey) key;
            return new RSAPublicKeySpec(rsaKey2.getModulus(), rsaKey2.getPublicExponent());
        } else if ((key instanceof PublicKey) && RSAPublicKeySpec.class.isAssignableFrom(keySpec)) {
            encoded = key.getEncoded();
            if (!"X.509".equals(key.getFormat()) || encoded == null) {
                throw new InvalidKeySpecException("Not a valid X.509 encoding");
            }
            RSAPublicKey rsaKey3 = (RSAPublicKey) engineGeneratePublic(new X509EncodedKeySpec(encoded));
            return new RSAPublicKeySpec(rsaKey3.getModulus(), rsaKey3.getPublicExponent());
        } else if ((key instanceof RSAPrivateCrtKey) && RSAPrivateCrtKeySpec.class.isAssignableFrom(keySpec)) {
            rsaKey = (RSAPrivateCrtKey) key;
            return new RSAPrivateCrtKeySpec(rsaKey.getModulus(), rsaKey.getPublicExponent(), rsaKey.getPrivateExponent(), rsaKey.getPrimeP(), rsaKey.getPrimeQ(), rsaKey.getPrimeExponentP(), rsaKey.getPrimeExponentQ(), rsaKey.getCrtCoefficient());
        } else if ((key instanceof RSAPrivateCrtKey) && RSAPrivateKeySpec.class.isAssignableFrom(keySpec)) {
            rsaKey = (RSAPrivateCrtKey) key;
            return new RSAPrivateKeySpec(rsaKey.getModulus(), rsaKey.getPrivateExponent());
        } else if ((key instanceof RSAPrivateKey) && RSAPrivateKeySpec.class.isAssignableFrom(keySpec)) {
            RSAPrivateKey rsaKey4 = (RSAPrivateKey) key;
            return new RSAPrivateKeySpec(rsaKey4.getModulus(), rsaKey4.getPrivateExponent());
        } else if ((key instanceof PrivateKey) && RSAPrivateCrtKeySpec.class.isAssignableFrom(keySpec)) {
            encoded = key.getEncoded();
            if (!"PKCS#8".equals(key.getFormat()) || encoded == null) {
                throw new InvalidKeySpecException("Not a valid PKCS#8 encoding");
            }
            privKey = (RSAPrivateKey) engineGeneratePrivate(new PKCS8EncodedKeySpec(encoded));
            if (privKey instanceof RSAPrivateCrtKey) {
                RSAPrivateCrtKey rsaKey5 = (RSAPrivateCrtKey) privKey;
                return new RSAPrivateCrtKeySpec(rsaKey5.getModulus(), rsaKey5.getPublicExponent(), rsaKey5.getPrivateExponent(), rsaKey5.getPrimeP(), rsaKey5.getPrimeQ(), rsaKey5.getPrimeExponentP(), rsaKey5.getPrimeExponentQ(), rsaKey5.getCrtCoefficient());
            }
            throw new InvalidKeySpecException("Encoded key is not an RSAPrivateCrtKey");
        } else if ((key instanceof PrivateKey) && RSAPrivateKeySpec.class.isAssignableFrom(keySpec)) {
            encoded = key.getEncoded();
            if (!"PKCS#8".equals(key.getFormat()) || encoded == null) {
                throw new InvalidKeySpecException("Not a valid PKCS#8 encoding");
            }
            privKey = (RSAPrivateKey) engineGeneratePrivate(new PKCS8EncodedKeySpec(encoded));
            return new RSAPrivateKeySpec(privKey.getModulus(), privKey.getPrivateExponent());
        } else if ((key instanceof PrivateKey) && PKCS8EncodedKeySpec.class.isAssignableFrom(keySpec)) {
            encoded = key.getEncoded();
            if (!"PKCS#8".equals(key.getFormat())) {
                stringBuilder = new StringBuilder();
                stringBuilder.append("Encoding type must be PKCS#8; was ");
                stringBuilder.append(key.getFormat());
                throw new InvalidKeySpecException(stringBuilder.toString());
            } else if (encoded != null) {
                return new PKCS8EncodedKeySpec(encoded);
            } else {
                throw new InvalidKeySpecException("Key is not encodable");
            }
        } else if ((key instanceof PublicKey) && X509EncodedKeySpec.class.isAssignableFrom(keySpec)) {
            encoded = key.getEncoded();
            if (!"X.509".equals(key.getFormat())) {
                stringBuilder = new StringBuilder();
                stringBuilder.append("Encoding type must be X.509; was ");
                stringBuilder.append(key.getFormat());
                throw new InvalidKeySpecException(stringBuilder.toString());
            } else if (encoded != null) {
                return new X509EncodedKeySpec(encoded);
            } else {
                throw new InvalidKeySpecException("Key is not encodable");
            }
        } else {
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("Unsupported key type and key spec combination; key=");
            stringBuilder2.append(key.getClass().getName());
            stringBuilder2.append(", keySpec=");
            stringBuilder2.append(keySpec.getName());
            throw new InvalidKeySpecException(stringBuilder2.toString());
        }
    }

    protected Key engineTranslateKey(Key key) throws InvalidKeyException {
        Key key2 = key;
        if (key2 == null) {
            throw new InvalidKeyException("key == null");
        } else if ((key2 instanceof OpenSSLRSAPublicKey) || (key2 instanceof OpenSSLRSAPrivateKey)) {
            return key2;
        } else {
            byte[] encoded;
            if (key2 instanceof RSAPublicKey) {
                RSAPublicKey rsaKey = (RSAPublicKey) key2;
                try {
                    return engineGeneratePublic(new RSAPublicKeySpec(rsaKey.getModulus(), rsaKey.getPublicExponent()));
                } catch (InvalidKeySpecException e) {
                    throw new InvalidKeyException(e);
                }
            } else if (key2 instanceof RSAPrivateCrtKey) {
                RSAPrivateCrtKey rsaKey2 = (RSAPrivateCrtKey) key2;
                try {
                    return engineGeneratePrivate(new RSAPrivateCrtKeySpec(rsaKey2.getModulus(), rsaKey2.getPublicExponent(), rsaKey2.getPrivateExponent(), rsaKey2.getPrimeP(), rsaKey2.getPrimeQ(), rsaKey2.getPrimeExponentP(), rsaKey2.getPrimeExponentQ(), rsaKey2.getCrtCoefficient()));
                } catch (InvalidKeySpecException e2) {
                    throw new InvalidKeyException(e2);
                }
            } else if (key2 instanceof RSAPrivateKey) {
                RSAPrivateKey rsaKey3 = (RSAPrivateKey) key2;
                try {
                    return engineGeneratePrivate(new RSAPrivateKeySpec(rsaKey3.getModulus(), rsaKey3.getPrivateExponent()));
                } catch (InvalidKeySpecException e22) {
                    throw new InvalidKeyException(e22);
                }
            } else if ((key2 instanceof PrivateKey) && "PKCS#8".equals(key.getFormat())) {
                encoded = key.getEncoded();
                if (encoded != null) {
                    try {
                        return engineGeneratePrivate(new PKCS8EncodedKeySpec(encoded));
                    } catch (InvalidKeySpecException e222) {
                        throw new InvalidKeyException(e222);
                    }
                }
                throw new InvalidKeyException("Key does not support encoding");
            } else if ((key2 instanceof PublicKey) && "X.509".equals(key.getFormat())) {
                encoded = key.getEncoded();
                if (encoded != null) {
                    try {
                        return engineGeneratePublic(new X509EncodedKeySpec(encoded));
                    } catch (InvalidKeySpecException e2222) {
                        throw new InvalidKeyException(e2222);
                    }
                }
                throw new InvalidKeyException("Key does not support encoding");
            } else {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Key must be an RSA public or private key; was ");
                stringBuilder.append(key.getClass().getName());
                throw new InvalidKeyException(stringBuilder.toString());
            }
        }
    }
}