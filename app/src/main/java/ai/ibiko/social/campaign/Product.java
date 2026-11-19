package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing the business sells, saved once in the catalog: its name, price, a few words about it and a photo on the
 * phone. Campaigns put the real photo in their pictures and the real price in their posts and replies, so neither is
 * ever made up. Pure Java.
 */
public final class Product {
    public String id = "", name = "", price = "", about = "", photo = "";

    Map<String, Object> json() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("name", name);
        m.put("price", price);
        m.put("about", about);
        m.put("photo", photo);
        return m;
    }

    static Product of(Object o) {
        Map<String, Object> m = MiniJson.obj(o);
        Product p = new Product();
        p.id = MiniJson.str(m.get("id"), "");
        p.name = MiniJson.str(m.get("name"), "");
        p.price = MiniJson.str(m.get("price"), "");
        p.about = MiniJson.str(m.get("about"), "");
        p.photo = MiniJson.str(m.get("photo"), "");
        return p;
    }

    /** A copy (a campaign keeps the products as they were when it was planned). */
    public Product copy() {
        return of(json());
    }

    /** The product as one line for the brain: "Stun torch, ₦18,000: rechargeable, 2 modes". */
    public String line() {
        StringBuilder s = new StringBuilder(name.trim());
        if (!price.trim().isEmpty()) {
            s.append(", ").append(price.trim());
        }
        String a = about.replaceAll("\\s+", " ").trim();
        if (!a.isEmpty()) {
            s.append(": ").append(a.length() > 200 ? a.substring(0, 200) + "…" : a);
        }
        return s.toString();
    }
}
