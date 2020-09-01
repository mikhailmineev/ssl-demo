# show client certs
openssl s_client -connect localhost:32768 -showcerts

# trust ssl
curl --cacert trust_root.crt https://localhost:32769 -vvv

# client ssl
curl -k --cert localhost.crt --key localhost.key --cacert trust_root.crt -v https://localhost:32786

# client ssl creation
https://www.baeldung.com/x-509-authentication-in-spring-security