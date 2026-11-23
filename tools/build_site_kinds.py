#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/web/sites.tsv.ibk: what Ibiko v1 knows about kinds of websites.

One line a kind: id, the words that name it (a regular expression), its name, what it is for, its sections (as
"block:Heading" pairs, the blocks Ibiko's site builder draws), the features it needs, the design patterns that suit it,
what it is usually built with, and its colours (background, surface, accent, text). Coded like the rest of the
knowledge (tools/encode_text.py), read back by ai.ibiko.ibiko1.TextCode.

Usage: tools/build_site_kinds.py
"""
import os
import subprocess
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "knowledge", "web", "sites.tsv")

BLOCKS = {"hero", "about", "services", "menu", "products", "gallery", "testimonials", "pricing", "faq", "contact", "hours",
          "schedule", "team", "stats", "cta", "posts", "countdown", "rsvp", "booking", "listings", "programmes", "donate",
          "features", "how", "newsletter", "skills"}

KINDS = [
    ("landing", r"landing page|one[- ]page|single[- ]page|product launch|launch page|squeeze page",
     "landing page", "gets visitors to take one action: sign up, buy, or message you",
     "hero:One clear offer;features:Why it helps you;how:How it works;testimonials:What people say;pricing:Simple pricing;faq:Questions;cta:Get started today;contact:Talk to us",
     "one clear call-to-action button repeated down the page; a sign-up or WhatsApp button; fast loading; a visit counter (analytics)",
     "Z-pattern layout; a single column on phones; a sticky header; social proof (reviews) right next to the button",
     "plain HTML, CSS and JavaScript, or a page builder (Carrd, Webflow, WordPress)", "#0f172a,#111c33,#38bdf8,#f8fafc"),
    ("business", r"business|company|corporate|firm|enterprise|services company|my services",
     "business website", "tells people what the business does and makes it easy to contact or hire it",
     "hero:Welcome;services:What we do;about:About us;stats:In numbers;testimonials:What clients say;cta:Work with us;contact:Contact us",
     "service cards; a contact form; a WhatsApp chat button; tap-to-call phone number; a map; working hours",
     "a card grid for services; trust signals (clients, years, reviews) high on the page; one main call to action",
     "HTML/CSS/JS or WordPress; a form service or a small backend for the contact form", "#ffffff,#f1f5f9,#1d4ed8,#0f172a"),
    ("restaurant", r"restaurant|bakery|cafe|café|eatery|kitchen|food|bar|lounge|chops|catering|pizza|grill|buka|canteen",
     "restaurant or food website", "shows the food and gets people to order, book a table or visit",
     "hero:Fresh, made for you;menu:Our menu;hours:Opening hours;about:Our story;gallery:From our kitchen;testimonials:Happy customers;contact:Order or visit us",
     "a menu with prices; ordering on WhatsApp; a table booking form; opening hours; a map and directions; food photos",
     "big appetising photos; a menu laid out in clear sections; the order button always in reach on phones",
     "HTML/CSS/JS, WordPress, or an ordering platform (Chowdeck, Glovo) linked from the site", "#1c1917,#292524,#f59e0b,#fafaf9"),
    ("shop", r"online shop|online store|e-?commerce|webshop|web shop|store|boutique|sell (?:my )?(?:products|things|goods|clothes) online|marketplace",
     "online shop (e-commerce)", "lets customers browse products, add them to a cart and pay",
     "hero:New this week;products:Shop the collection;features:Why buy from us;testimonials:Reviews;faq:Delivery and returns;contact:Need help?",
     "product cards with photos and prices; a cart; checkout and payment (Paystack, Flutterwave, Stripe); search and filters; delivery and returns information; order tracking",
     "a product card grid; F-pattern browsing; price and the buy button always visible; few steps to pay",
     "Shopify, WooCommerce (WordPress), or a custom site with a payment API and a database", "#ffffff,#f8fafc,#db2777,#111827"),
    ("portfolio", r"portfolio|showcase (?:my )?work|my work|designer|photographer|artist|illustrator|creative",
     "portfolio", "shows your best work so people hire or commission you",
     "hero:Hi, I make things;gallery:Selected work;about:About me;skills:What I do;testimonials:Kind words;contact:Let's work together",
     "a project gallery that opens each piece large; a short bio; skills; a downloadable CV; a contact form",
     "lots of white space; the work first and big; a masonry or even grid; few words",
     "HTML/CSS/JS, or Behance/Dribbble linked from a simple site", "#0a0a0a,#171717,#a78bfa,#fafafa"),
    ("blog", r"blog|blogging|articles|writing website|my writing",
     "blog", "publishes articles people come back to read and share",
     "hero:Notes and stories;posts:Latest posts;about:About the writer;newsletter:Get new posts by email;contact:Say hello",
     "a list of posts with dates and reading time; categories and tags; search; a newsletter sign-up; comments; an RSS feed",
     "a readable single column (60–75 characters a line); clear headings; the newest posts first",
     "WordPress, Ghost, Medium, or a static site generator (Hugo, Jekyll, Astro)", "#fffdf7,#f5f1e6,#b45309,#1f2937"),
    ("personal", r"personal (?:website|site|page)|resume|résumé|\bcv\b|about me page|my own website|portfolio site for me",
     "personal website or online CV", "introduces you: who you are, what you do, and how to reach you",
     "hero:Hello, I'm here;about:About me;skills:Skills;schedule:Experience;contact:Get in touch",
     "a short introduction; skills; work history; links to LinkedIn and GitHub; a downloadable CV; contact details",
     "one page; a friendly photo; scannable sections; dark text on a light background",
     "HTML/CSS/JS on GitHub Pages or Netlify (free)", "#ffffff,#f3f4f6,#059669,#111827"),
    ("school", r"school|academy|college|university|institute|tutorial|lesson|creche|nursery|education",
     "school website", "informs parents and students and takes admissions",
     "hero:Learning that lasts;about:About our school;programmes:Our programmes;stats:Our results;posts:News and events;gallery:School life;contact:Admissions and contact",
     "admissions information and a form; programmes and fees; a school calendar; news and events; staff; downloads (prospectus)",
     "trustworthy colours; clear navigation by who is visiting (parents, students, staff); news on the home page",
     "WordPress or a school portal; HTML/CSS/JS for a simple site", "#ffffff,#eff6ff,#1e40af,#0f172a"),
    ("church", r"church|mosque|ministry|fellowship|parish|chapel|religious|congregation",
     "church or ministry website", "welcomes visitors, shares service times and teaching, and takes giving",
     "hero:You are welcome here;schedule:Service times;posts:Recent messages;about:What we believe;donate:Give;contact:Visit us",
     "service times; sermons (audio, video, livestream); events; online giving; a prayer request form; directions",
     "warm, welcoming photos; service times on the home page; a clear Give button",
     "WordPress with a giving plugin, or HTML/CSS/JS with a payment link", "#fffbeb,#fef3c7,#7c2d12,#1c1917"),
    ("ngo", r"\bngo\b|charity|foundation|non-?profit|nonprofit|cause|volunteer",
     "charity or NGO website", "tells the cause's story and gets donations and volunteers",
     "hero:Together we can;about:Our mission;stats:Our impact;programmes:What we do;testimonials:Stories;donate:Donate;contact:Volunteer or partner",
     "a donate button; impact numbers; programmes; stories; a volunteer form; partners; reports",
     "emotional, real photos; impact in numbers; the donate button always visible",
     "WordPress with a donation plugin, or HTML/CSS/JS with Paystack or Flutterwave links", "#ffffff,#f0fdf4,#15803d,#052e16"),
    ("event", r"event|wedding|conference|concert|party|summit|festival|birthday|launch event|seminar",
     "event website", "gives the date and place and gets people to RSVP or buy tickets",
     "hero:Save the date;countdown:Counting down;schedule:Programme;about:About the event;gallery:Moments;rsvp:RSVP;contact:Getting there",
     "a countdown; the programme; speakers or the couple; venue and map; an RSVP or ticket form; add to calendar",
     "the date and place right at the top; one bold colour; RSVP always within reach",
     "HTML/CSS/JS, or Eventbrite for tickets linked from the site", "#18181b,#27272a,#f43f5e,#fafafa"),
    ("real_estate", r"real estate|property|properties|realtor|homes? for (?:sale|rent)|apartments? for|housing|land for sale",
     "real estate website", "lists properties and gets buyers or renters to enquire",
     "hero:Find your next home;listings:Featured properties;about:About us;testimonials:Happy clients;team:Our agents;contact:Book an inspection",
     "property search and filters (place, price, rooms); listing cards with photos and prices; a listing gallery; a map; an enquiry or inspection form",
     "a search box at the top; a listing card grid; big photos; price and place on every card",
     "WordPress with a property theme, or a custom site with a database", "#ffffff,#f8fafc,#0f766e,#0f172a"),
    ("salon", r"salon|barber|barbing|spa|beauty|makeup|nails|hair|lashes|stylist",
     "salon or beauty website", "shows the work and prices and gets bookings",
     "hero:Look and feel your best;services:Services and prices;gallery:Our work;booking:Book an appointment;testimonials:Reviews;contact:Find us",
     "services with prices; before-and-after photos; online booking; reviews; location and hours; Instagram link",
     "elegant, image-led; prices easy to find; the booking button in the header",
     "HTML/CSS/JS with a booking form, or a booking platform (Fresha, Booksy)", "#fdf2f8,#fce7f3,#be185d,#1f1020"),
    ("hotel", r"hotel|guest ?house|resort|lodge|short[- ]let|shortlet|airbnb|apartment rentals|inn\b",
     "hotel or short-let website", "shows the rooms and gets bookings",
     "hero:Stay with us;listings:Rooms;features:Amenities;gallery:Gallery;testimonials:Guest reviews;booking:Book your stay;contact:Location",
     "rooms with prices; amenities; a booking form (dates, guests); a gallery; reviews; location and directions",
     "big room photos; dates and the book button at the top; prices per night",
     "WordPress with a booking plugin, or a booking engine linked from the site", "#0c1222,#162033,#d4a373,#f8fafc"),
    ("clinic", r"clinic|hospital|doctor|dental|dentist|pharmacy|health|medical|physio|laboratory|diagnostic",
     "clinic or health website", "tells patients the services and gets appointments",
     "hero:Care you can trust;services:Our services;team:Our doctors;hours:Opening hours;booking:Book an appointment;faq:Questions;contact:Emergency and contact",
     "services; doctors; appointment booking; opening hours; emergency number; HMOs or insurance accepted; directions",
     "calm, clean colours; the emergency number visible; plain words",
     "HTML/CSS/JS or WordPress; a booking system for appointments", "#ffffff,#f0f9ff,#0369a1,#0c4a6e"),
    ("saas", r"saas|software product|startup|app website|website for (?:my|an|our) app|tech product|platform",
     "software product (SaaS) website", "explains the product and gets sign-ups or trials",
     "hero:Work faster;features:Features;how:How it works;pricing:Plans;testimonials:Loved by teams;faq:Questions;cta:Start free;contact:Talk to sales",
     "feature sections with screenshots; pricing tiers; a sign-up or free-trial button; integrations; documentation link; FAQ",
     "hero with a product screenshot; alternating feature rows; three pricing tiers with one highlighted",
     "Next.js/React or Astro; a backend and auth for the product itself", "#0b1020,#141a33,#6366f1,#eef2ff"),
    ("news", r"news|newspaper|media house|magazine|journal",
     "news or magazine website", "publishes stories and keeps readers coming back",
     "hero:Top story;posts:Latest;features:Sections;newsletter:Daily briefing;contact:Tips and contact",
     "headlines; categories; trending stories; search; a newsletter; ad slots; author pages",
     "a dense grid with a clear hierarchy (top story biggest); dates on every story",
     "WordPress or a headless CMS (Ghost, Strapi) with a fast front end", "#ffffff,#f4f4f5,#dc2626,#18181b"),
    ("gym", r"gym|fitness|trainer|workout|yoga|pilates|crossfit|bootcamp",
     "gym or fitness website", "shows classes and plans and gets people to join",
     "hero:Get stronger;programmes:Classes;schedule:Timetable;team:Trainers;pricing:Membership;testimonials:Results;contact:Join us",
     "classes; a timetable; trainers; membership plans; a free-trial or join form; before-and-after stories",
     "bold, energetic colours; action photos; the join button repeated",
     "HTML/CSS/JS or WordPress; a membership or payment link", "#0a0a0a,#1a1a1a,#84cc16,#fafafa"),
    ("agency", r"agency|marketing|digital agency|design studio|studio|advertising|branding|consultancy",
     "agency website", "shows the agency's work and wins clients",
     "hero:We grow brands;services:Services;gallery:Case studies;how:How we work;team:The team;testimonials:Clients;contact:Start a project",
     "services; case studies with results; the process; clients' logos; the team; a project enquiry form",
     "bold type; work shown with results in numbers; a confident single call to action",
     "HTML/CSS/JS, Webflow or WordPress", "#0f0f0f,#1c1c1c,#facc15,#f5f5f5"),
    ("professional", r"law firm|lawyer|solicitor|attorney|consulting|consultant|accounting|accountant|audit firm|architect",
     "professional services website", "builds trust and gets consultations booked",
     "hero:Advice you can rely on;services:Practice areas;about:Our firm;team:Our people;testimonials:Client results;booking:Book a consultation;contact:Contact",
     "practice areas; the team with credentials; client results; a consultation booking form; office address",
     "sober, trustworthy colours; credentials early; plenty of space",
     "WordPress or HTML/CSS/JS with a booking form", "#ffffff,#f8fafc,#1e3a8a,#0f172a"),
]


def main():
    lines = ["# Kinds of websites Ibiko v1 knows (tools/build_site_kinds.py): id, named by, name, for, sections, features, patterns, built with, colours"]
    for k in KINDS:
        assert len(k) == 9, k[0]
        for pair in k[4].split(";"):
            block = pair.split(":")[0]
            assert block in BLOCKS, (k[0], block)
        assert len(k[8].split(",")) == 4, k[0]
        lines.append("\t".join(k))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "encode_text.py"), OUT, "--remove"], check=True)
    print("%d kinds of website" % len(KINDS))


if __name__ == "__main__":
    main()
