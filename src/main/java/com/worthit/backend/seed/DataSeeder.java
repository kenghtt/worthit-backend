package com.worthit.backend.seed;

import com.worthit.backend.entity.Company;
import com.worthit.backend.entity.EmploymentStatus;
import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.ExperienceStatus;
import com.worthit.backend.entity.Level;
import com.worthit.backend.entity.CompanyRole;
import com.worthit.backend.entity.Location;
import com.worthit.backend.entity.Role;
import com.worthit.backend.repository.CompanyRepository;
import com.worthit.backend.repository.ExperienceRepository;
import com.worthit.backend.repository.CompanyRoleRepository;
import com.worthit.backend.repository.LevelRepository;
import com.worthit.backend.repository.LocationRepository;
import com.worthit.backend.repository.RoleRepository;
import com.worthit.backend.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Idempotent lookup seeder (backend_spec.md section 8 / build order step 3).
 *
 * <p>Seeds a few hundred well-known tech companies and major corporations, 4 global roles,
 * one location per distinct company headquarters, and per-company level ladders for the most
 * common employers. All inserts are upsert-by-slug / (company,name), so the seeder is safe to
 * run on every boot.</p>
 *
 * <p>Experiences are only seeded for the original demo companies (see {@link #EXPERIENCE_SEED_SLUGS});
 * the additional companies are populated for typeahead/lookup purposes only, with no experiences.</p>
 *
 * <p>Gated by {@code app.seed.enabled} (default {@code true}). Disable it via property to skip
 * seeding (e.g. in unit tests or when running against a pre-populated DB).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final LocationRepository locationRepository;
    private final LevelRepository levelRepository;
    private final ExperienceRepository experienceRepository;
    private final CompanyRoleRepository companyRoleRepository;

    /** How many experiences to seed per company. */
    private static final int EXPERIENCES_PER_COMPANY = 5;

    /**
     * Slugs of the original demo companies that get sample experiences seeded. The much larger
     * catalog of additional companies is seeded for typeahead/lookup only and intentionally gets
     * NO experiences.
     */
    private static final Set<String> EXPERIENCE_SEED_SLUGS = Set.of(
            "amazon", "google", "meta", "microsoft", "stripe", "netflix", "apple", "uber",
            "airbnb", "salesforce", "tesla", "linkedin", "coinbase", "snap", "lyft", "doordash",
            "robinhood", "adobe", "dropbox", "instacart"
    );

    private static final List<String[]> COMPANIES = List.of(
            // {name, industry, headquarters}
            new String[]{"Amazon", "Tech", "Seattle, WA"},
            new String[]{"Google", "Tech", "Mountain View, CA"},
            new String[]{"Meta", "Tech", "Menlo Park, CA"},
            new String[]{"Microsoft", "Tech", "Redmond, WA"},
            new String[]{"Stripe", "Fintech", "San Francisco, CA"},
            new String[]{"Netflix", "Media", "Los Gatos, CA"},
            new String[]{"Apple", "Tech", "Cupertino, CA"},
            new String[]{"Uber", "Tech", "San Francisco, CA"},
            new String[]{"Airbnb", "Tech", "San Francisco, CA"},
            new String[]{"Salesforce", "Tech", "San Francisco, CA"},
            new String[]{"Tesla", "Automotive", "Austin, TX"},
            new String[]{"LinkedIn", "Tech", "Sunnyvale, CA"},
            new String[]{"Coinbase", "Fintech", "Remote"},
            new String[]{"Snap", "Media", "Los Angeles, CA"},
            new String[]{"Lyft", "Tech", "San Francisco, CA"},
            new String[]{"DoorDash", "Tech", "San Francisco, CA"},
            new String[]{"Robinhood", "Fintech", "Menlo Park, CA"},
            new String[]{"Adobe", "Tech", "San Jose, CA"},
            new String[]{"Dropbox", "Tech", "San Francisco, CA"},
            new String[]{"Instacart", "Tech", "San Francisco, CA"},

            // Big Tech / FAANG-adjacent
            new String[]{"Nvidia", "Semiconductors", "Santa Clara, CA"},
            new String[]{"Intel", "Semiconductors", "Santa Clara, CA"},
            new String[]{"AMD", "Semiconductors", "Santa Clara, CA"},
            new String[]{"Qualcomm", "Semiconductors", "San Diego, CA"},
            new String[]{"Broadcom", "Semiconductors", "Palo Alto, CA"},
            new String[]{"Texas Instruments", "Semiconductors", "Dallas, TX"},
            new String[]{"Micron", "Semiconductors", "Boise, ID"},
            new String[]{"Oracle", "Enterprise", "Austin, TX"},
            new String[]{"IBM", "Enterprise", "Armonk, NY"},
            new String[]{"SAP", "Enterprise", "Walldorf, DE"},
            new String[]{"Cisco", "Networking", "San Jose, CA"},
            new String[]{"VMware", "Enterprise", "Palo Alto, CA"},
            new String[]{"Dell", "Hardware", "Round Rock, TX"},
            new String[]{"HP", "Hardware", "Palo Alto, CA"},
            new String[]{"HPE", "Enterprise", "Houston, TX"},
            new String[]{"Nokia", "Networking", "Espoo, FI"},
            new String[]{"Ericsson", "Networking", "Stockholm, SE"},
            new String[]{"Samsung", "Hardware", "Suwon, KR"},
            new String[]{"Sony", "Media", "Tokyo, JP"},
            new String[]{"Tencent", "Tech", "Shenzhen, CN"},
            new String[]{"Alibaba", "Tech", "Hangzhou, CN"},
            new String[]{"ByteDance", "Tech", "Beijing, CN"},
            new String[]{"TikTok", "Media", "Culver City, CA"},
            new String[]{"Baidu", "Tech", "Beijing, CN"},

            // SaaS / Enterprise software
            new String[]{"Atlassian", "Enterprise", "Sydney, AU"},
            new String[]{"Workday", "Enterprise", "Pleasanton, CA"},
            new String[]{"ServiceNow", "Enterprise", "Santa Clara, CA"},
            new String[]{"Snowflake", "Enterprise", "Bozeman, MT"},
            new String[]{"Databricks", "Enterprise", "San Francisco, CA"},
            new String[]{"MongoDB", "Enterprise", "New York, NY"},
            new String[]{"Elastic", "Enterprise", "Mountain View, CA"},
            new String[]{"Confluent", "Enterprise", "Mountain View, CA"},
            new String[]{"HashiCorp", "Enterprise", "San Francisco, CA"},
            new String[]{"GitLab", "Enterprise", "Remote"},
            new String[]{"GitHub", "Enterprise", "San Francisco, CA"},
            new String[]{"Twilio", "Enterprise", "San Francisco, CA"},
            new String[]{"Okta", "Enterprise", "San Francisco, CA"},
            new String[]{"Datadog", "Enterprise", "New York, NY"},
            new String[]{"Splunk", "Enterprise", "San Francisco, CA"},
            new String[]{"Cloudflare", "Enterprise", "San Francisco, CA"},
            new String[]{"Fastly", "Enterprise", "San Francisco, CA"},
            new String[]{"DigitalOcean", "Cloud", "New York, NY"},
            new String[]{"Zoom", "Enterprise", "San Jose, CA"},
            new String[]{"Slack", "Enterprise", "San Francisco, CA"},
            new String[]{"Asana", "Enterprise", "San Francisco, CA"},
            new String[]{"Notion", "Enterprise", "San Francisco, CA"},
            new String[]{"Figma", "Enterprise", "San Francisco, CA"},
            new String[]{"Canva", "Enterprise", "Sydney, AU"},
            new String[]{"Miro", "Enterprise", "San Francisco, CA"},
            new String[]{"DocuSign", "Enterprise", "San Francisco, CA"},
            new String[]{"Dropbox Sign", "Enterprise", "San Francisco, CA"},
            new String[]{"Box", "Enterprise", "Redwood City, CA"},
            new String[]{"Zendesk", "Enterprise", "San Francisco, CA"},
            new String[]{"HubSpot", "Enterprise", "Cambridge, MA"},
            new String[]{"Intuit", "Fintech", "Mountain View, CA"},
            new String[]{"Autodesk", "Enterprise", "San Francisco, CA"},
            new String[]{"Unity", "Gaming", "San Francisco, CA"},
            new String[]{"Epic Games", "Gaming", "Cary, NC"},
            new String[]{"Roblox", "Gaming", "San Mateo, CA"},
            new String[]{"Electronic Arts", "Gaming", "Redwood City, CA"},
            new String[]{"Activision Blizzard", "Gaming", "Santa Monica, CA"},
            new String[]{"Riot Games", "Gaming", "Los Angeles, CA"},
            new String[]{"Take-Two Interactive", "Gaming", "New York, NY"},
            new String[]{"Nintendo", "Gaming", "Redmond, WA"},
            new String[]{"Valve", "Gaming", "Bellevue, WA"},

            // Fintech / Finance
            new String[]{"PayPal", "Fintech", "San Jose, CA"},
            new String[]{"Block", "Fintech", "San Francisco, CA"},
            new String[]{"Square", "Fintech", "San Francisco, CA"},
            new String[]{"Visa", "Fintech", "Foster City, CA"},
            new String[]{"Mastercard", "Fintech", "Purchase, NY"},
            new String[]{"American Express", "Fintech", "New York, NY"},
            new String[]{"Plaid", "Fintech", "San Francisco, CA"},
            new String[]{"Affirm", "Fintech", "San Francisco, CA"},
            new String[]{"Brex", "Fintech", "San Francisco, CA"},
            new String[]{"Ramp", "Fintech", "New York, NY"},
            new String[]{"Chime", "Fintech", "San Francisco, CA"},
            new String[]{"SoFi", "Fintech", "San Francisco, CA"},
            new String[]{"Wise", "Fintech", "London, UK"},
            new String[]{"Revolut", "Fintech", "London, UK"},
            new String[]{"Klarna", "Fintech", "Stockholm, SE"},
            new String[]{"Goldman Sachs", "Bank", "New York, NY"},
            new String[]{"JPMorgan Chase", "Bank", "New York, NY"},
            new String[]{"Morgan Stanley", "Bank", "New York, NY"},
            new String[]{"Citi", "Bank", "New York, NY"},
            new String[]{"Bank of America", "Bank", "Charlotte, NC"},
            new String[]{"Wells Fargo", "Bank", "San Francisco, CA"},
            new String[]{"Capital One", "Bank", "McLean, VA"},
            new String[]{"Bloomberg", "Fintech", "New York, NY"},
            new String[]{"Two Sigma", "Finance", "New York, NY"},
            new String[]{"Citadel", "Finance", "Miami, FL"},
            new String[]{"Jane Street", "Finance", "New York, NY"},
            new String[]{"Hudson River Trading", "Finance", "New York, NY"},
            new String[]{"Jump Trading", "Finance", "Chicago, IL"},
            new String[]{"DE Shaw", "Finance", "New York, NY"},

            // Consumer / Internet
            new String[]{"Pinterest", "Social", "San Francisco, CA"},
            new String[]{"Reddit", "Social", "San Francisco, CA"},
            new String[]{"Discord", "Social", "San Francisco, CA"},
            new String[]{"Spotify", "Media", "Stockholm, SE"},
            new String[]{"Twitch", "Media", "San Francisco, CA"},
            new String[]{"X", "Social", "San Francisco, CA"},
            new String[]{"Twitter", "Social", "San Francisco, CA"},
            new String[]{"Yelp", "Tech", "San Francisco, CA"},
            new String[]{"Etsy", "E-commerce", "Brooklyn, NY"},
            new String[]{"eBay", "E-commerce", "San Jose, CA"},
            new String[]{"Shopify", "E-commerce", "Ottawa, CA"},
            new String[]{"Wayfair", "E-commerce", "Boston, MA"},
            new String[]{"Chewy", "E-commerce", "Plantation, FL"},
            new String[]{"Booking.com", "Travel", "Amsterdam, NL"},
            new String[]{"Expedia", "Travel", "Seattle, WA"},
            new String[]{"TripAdvisor", "Travel", "Needham, MA"},
            new String[]{"Grubhub", "Tech", "Chicago, IL"},
            new String[]{"Postmates", "Tech", "San Francisco, CA"},
            new String[]{"Grab", "Tech", "Singapore, SG"},
            new String[]{"Gojek", "Tech", "Jakarta, ID"},
            new String[]{"Mercado Libre", "E-commerce", "Buenos Aires, AR"},
            new String[]{"Nubank", "Fintech", "Sao Paulo, BR"},
            new String[]{"Rappi", "Tech", "Bogota, CO"},
            new String[]{"Zomato", "Tech", "Gurgaon, IN"},
            new String[]{"Swiggy", "Tech", "Bangalore, IN"},
            new String[]{"Flipkart", "E-commerce", "Bangalore, IN"},
            new String[]{"Paytm", "Fintech", "Noida, IN"},
            new String[]{"Razorpay", "Fintech", "Bangalore, IN"},
            new String[]{"Zoho", "Enterprise", "Chennai, IN"},
            new String[]{"Infosys", "Consulting", "Bangalore, IN"},
            new String[]{"Tata Consultancy Services", "Consulting", "Mumbai, IN"},
            new String[]{"Wipro", "Consulting", "Bangalore, IN"},
            new String[]{"HCLTech", "Consulting", "Noida, IN"},
            new String[]{"Cognizant", "Consulting", "Teaneck, NJ"},
            new String[]{"Accenture", "Consulting", "Dublin, IE"},
            new String[]{"Deloitte", "Consulting", "London, UK"},
            new String[]{"McKinsey", "Consulting", "New York, NY"},
            new String[]{"Boston Consulting Group", "Consulting", "Boston, MA"},
            new String[]{"Bain", "Consulting", "Boston, MA"},
            new String[]{"Capgemini", "Consulting", "Paris, FR"},
            new String[]{"EPAM", "Consulting", "Newtown, PA"},
            new String[]{"Thoughtworks", "Consulting", "Chicago, IL"},

            // AI / ML
            new String[]{"OpenAI", "AI", "San Francisco, CA"},
            new String[]{"Anthropic", "AI", "San Francisco, CA"},
            new String[]{"Hugging Face", "AI", "New York, NY"},
            new String[]{"Scale AI", "AI", "San Francisco, CA"},
            new String[]{"Cohere", "AI", "Toronto, CA"},
            new String[]{"Mistral AI", "AI", "Paris, FR"},
            new String[]{"DeepMind", "AI", "London, UK"},
            new String[]{"Palantir", "Enterprise", "Denver, CO"},
            new String[]{"C3.ai", "AI", "Redwood City, CA"},

            // Hardware / Auto / Aerospace / Industrial
            new String[]{"Rivian", "Automotive", "Irvine, CA"},
            new String[]{"Lucid Motors", "Automotive", "Newark, CA"},
            new String[]{"Ford", "Automotive", "Dearborn, MI"},
            new String[]{"General Motors", "Automotive", "Detroit, MI"},
            new String[]{"Toyota", "Automotive", "Toyota City, JP"},
            new String[]{"Waymo", "Automotive", "Mountain View, CA"},
            new String[]{"Cruise", "Automotive", "San Francisco, CA"},
            new String[]{"Aurora", "Automotive", "Pittsburgh, PA"},
            new String[]{"SpaceX", "Aerospace", "Hawthorne, CA"},
            new String[]{"Blue Origin", "Aerospace", "Kent, WA"},
            new String[]{"Boeing", "Aerospace", "Arlington, VA"},
            new String[]{"Lockheed Martin", "Aerospace", "Bethesda, MD"},
            new String[]{"Northrop Grumman", "Aerospace", "Falls Church, VA"},
            new String[]{"Raytheon", "Aerospace", "Arlington, VA"},
            new String[]{"Anduril", "Defense", "Costa Mesa, CA"},
            new String[]{"GE", "Industrial", "Boston, MA"},
            new String[]{"Siemens", "Industrial", "Munich, DE"},
            new String[]{"Honeywell", "Industrial", "Charlotte, NC"},
            new String[]{"Bosch", "Industrial", "Gerlingen, DE"},
            new String[]{"ASML", "Semiconductors", "Veldhoven, NL"},
            new String[]{"TSMC", "Semiconductors", "Hsinchu, TW"},
            new String[]{"Arm", "Semiconductors", "Cambridge, UK"},
            new String[]{"Western Digital", "Hardware", "San Jose, CA"},
            new String[]{"Seagate", "Hardware", "Fremont, CA"},

            // Retail / Consumer / Telecom / Media / Health
            new String[]{"Walmart", "Retail", "Bentonville, AR"},
            new String[]{"Target", "Retail", "Minneapolis, MN"},
            new String[]{"Costco", "Retail", "Issaquah, WA"},
            new String[]{"Home Depot", "Retail", "Atlanta, GA"},
            new String[]{"Nike", "Consumer", "Beaverton, OR"},
            new String[]{"Starbucks", "Consumer", "Seattle, WA"},
            new String[]{"Procter & Gamble", "Consumer", "Cincinnati, OH"},
            new String[]{"Coca-Cola", "Consumer", "Atlanta, GA"},
            new String[]{"PepsiCo", "Consumer", "Purchase, NY"},
            new String[]{"Disney", "Media", "Burbank, CA"},
            new String[]{"Warner Bros Discovery", "Media", "New York, NY"},
            new String[]{"Comcast", "Media", "Philadelphia, PA"},
            new String[]{"AT&T", "Telecom", "Dallas, TX"},
            new String[]{"Verizon", "Telecom", "New York, NY"},
            new String[]{"T-Mobile", "Telecom", "Bellevue, WA"},
            new String[]{"UnitedHealth Group", "Healthcare", "Minnetonka, MN"},
            new String[]{"CVS Health", "Healthcare", "Woonsocket, RI"},
            new String[]{"Johnson & Johnson", "Healthcare", "New Brunswick, NJ"},
            new String[]{"Pfizer", "Healthcare", "New York, NY"},
            new String[]{"Moderna", "Healthcare", "Cambridge, MA"},
            new String[]{"Epic Systems", "Healthcare", "Verona, WI"},
            new String[]{"Cerner", "Healthcare", "Kansas City, MO"},
            new String[]{"Teladoc", "Healthcare", "Purchase, NY"},
            new String[]{"Roche", "Healthcare", "Basel, CH"},
            new String[]{"Novartis", "Healthcare", "Basel, CH"},

            // Notable startups / scale-ups
            new String[]{"Plenty", "Tech", "San Francisco, CA"},
            new String[]{"Gusto", "Fintech", "San Francisco, CA"},
            new String[]{"Rippling", "Enterprise", "San Francisco, CA"},
            new String[]{"Deel", "Enterprise", "San Francisco, CA"},
            new String[]{"Carta", "Fintech", "San Francisco, CA"},
            new String[]{"Flexport", "Tech", "San Francisco, CA"},
            new String[]{"Samsara", "Enterprise", "San Francisco, CA"},
            new String[]{"Toast", "Fintech", "Boston, MA"},
            new String[]{"Nextdoor", "Social", "San Francisco, CA"},
            new String[]{"Reddit Inc", "Social", "San Francisco, CA"},
            new String[]{"Faire", "E-commerce", "San Francisco, CA"},
            new String[]{"Gradle", "Enterprise", "San Francisco, CA"},
            new String[]{"JetBrains", "Enterprise", "Prague, CZ"},
            new String[]{"Grammarly", "Tech", "San Francisco, CA"},
            new String[]{"Coursera", "EdTech", "Mountain View, CA"},
            new String[]{"Duolingo", "EdTech", "Pittsburgh, PA"},
            new String[]{"Udemy", "EdTech", "San Francisco, CA"},
            new String[]{"Khan Academy", "EdTech", "Mountain View, CA"},
            new String[]{"Squarespace", "Tech", "New York, NY"},
            new String[]{"Wix", "Tech", "Tel Aviv, IL"},
            new String[]{"Automattic", "Tech", "Remote"},
            new String[]{"Cloudera", "Enterprise", "Santa Clara, CA"},
            new String[]{"Nutanix", "Enterprise", "San Jose, CA"},
            new String[]{"Pure Storage", "Hardware", "Santa Clara, CA"},
            new String[]{"NetApp", "Hardware", "San Jose, CA"},
            new String[]{"Akamai", "Networking", "Cambridge, MA"},
            new String[]{"Palo Alto Networks", "Security", "Santa Clara, CA"},
            new String[]{"CrowdStrike", "Security", "Austin, TX"},
            new String[]{"Fortinet", "Security", "Sunnyvale, CA"},
            new String[]{"Zscaler", "Security", "San Jose, CA"},
            new String[]{"SentinelOne", "Security", "Mountain View, CA"},
            new String[]{"Cloudbees", "Enterprise", "San Jose, CA"},
            new String[]{"PagerDuty", "Enterprise", "San Francisco, CA"},
            new String[]{"New Relic", "Enterprise", "San Francisco, CA"},
            new String[]{"Segment", "Enterprise", "San Francisco, CA"},
            new String[]{"Amplitude", "Enterprise", "San Francisco, CA"},
            new String[]{"Mixpanel", "Enterprise", "San Francisco, CA"},
            new String[]{"Airtable", "Enterprise", "San Francisco, CA"},
            new String[]{"Smartsheet", "Enterprise", "Bellevue, WA"},
            new String[]{"Coupang", "E-commerce", "Seoul, KR"},
            new String[]{"Sea Group", "Tech", "Singapore, SG"},
            new String[]{"Yandex", "Tech", "Moscow, RU"},
            new String[]{"Naver", "Tech", "Seongnam, KR"},
            new String[]{"Line", "Tech", "Tokyo, JP"},
            new String[]{"Rakuten", "E-commerce", "Tokyo, JP"},
            new String[]{"Mercari", "E-commerce", "Tokyo, JP"},
            new String[]{"Delivery Hero", "Tech", "Berlin, DE"},
            new String[]{"Zalando", "E-commerce", "Berlin, DE"},
            new String[]{"Spotify Technology", "Media", "Stockholm, SE"},
            new String[]{"Adyen", "Fintech", "Amsterdam, NL"},
            new String[]{"Checkout.com", "Fintech", "London, UK"},
            new String[]{"Monzo", "Fintech", "London, UK"},
            new String[]{"N26", "Fintech", "Berlin, DE"},
            new String[]{"UiPath", "Enterprise", "New York, NY"},
            new String[]{"Bumble", "Social", "Austin, TX"},
            new String[]{"Match Group", "Social", "Dallas, TX"},
            new String[]{"Roku", "Media", "San Jose, CA"},
            new String[]{"Sonos", "Hardware", "Santa Barbara, CA"},
            new String[]{"Fitbit", "Hardware", "San Francisco, CA"},
            new String[]{"Garmin", "Hardware", "Olathe, KS"},
            new String[]{"GoPro", "Hardware", "San Mateo, CA"},
            new String[]{"DJI", "Hardware", "Shenzhen, CN"},
            new String[]{"Xiaomi", "Hardware", "Beijing, CN"},
            new String[]{"Huawei", "Networking", "Shenzhen, CN"},
            new String[]{"Lenovo", "Hardware", "Beijing, CN"},
            new String[]{"Asus", "Hardware", "Taipei, TW"},
            new String[]{"Acer", "Hardware", "New Taipei, TW"},
            new String[]{"Logitech", "Hardware", "Lausanne, CH"}
    );

    private static final List<String[]> ROLES = List.of(
            // {name, family}
            new String[]{"Software Engineer", "Engineering"},
            new String[]{"Director Of Engineering", "Engineering"},
            new String[]{"Product Owner", "Product"},
            new String[]{"Engineering Manager", "Engineering"},
            // Example company-specific catalog roles that might not exist everywhere
            new String[]{"ML Engineer", "Engineering"},
            new String[]{"Network Engineer", "Network"}
    );

    /**
     * Locations are derived directly from the {@link #COMPANIES} headquarters values
     * (format {@code "City, ST"}). Every distinct headquarters city/region is represented here,
     * and nothing that isn't a company headquarters is included. The non-physical {@code "Remote"}
     * headquarters value is intentionally excluded since it has no city/state.
     */
    private static final List<String[]> LOCATIONS = List.of(
            // {city, state/country} — one entry per distinct company headquarters
            new String[]{"Seattle", "WA"},
            new String[]{"Mountain View", "CA"},
            new String[]{"Menlo Park", "CA"},
            new String[]{"Redmond", "WA"},
            new String[]{"San Francisco", "CA"},
            new String[]{"Los Gatos", "CA"},
            new String[]{"Cupertino", "CA"},
            new String[]{"Austin", "TX"},
            new String[]{"Sunnyvale", "CA"},
            new String[]{"Los Angeles", "CA"},
            new String[]{"San Jose", "CA"},
            new String[]{"Santa Clara", "CA"},
            new String[]{"San Diego", "CA"},
            new String[]{"Palo Alto", "CA"},
            new String[]{"Dallas", "TX"},
            new String[]{"Boise", "ID"},
            new String[]{"Armonk", "NY"},
            new String[]{"Walldorf", "DE"},
            new String[]{"Round Rock", "TX"},
            new String[]{"Houston", "TX"},
            new String[]{"Espoo", "FI"},
            new String[]{"Stockholm", "SE"},
            new String[]{"Suwon", "KR"},
            new String[]{"Tokyo", "JP"},
            new String[]{"Shenzhen", "CN"},
            new String[]{"Hangzhou", "CN"},
            new String[]{"Beijing", "CN"},
            new String[]{"Culver City", "CA"},
            new String[]{"Sydney", "AU"},
            new String[]{"Pleasanton", "CA"},
            new String[]{"Bozeman", "MT"},
            new String[]{"New York", "NY"},
            new String[]{"Cambridge", "MA"},
            new String[]{"Redwood City", "CA"},
            new String[]{"Cary", "NC"},
            new String[]{"San Mateo", "CA"},
            new String[]{"Santa Monica", "CA"},
            new String[]{"Bellevue", "WA"},
            new String[]{"Foster City", "CA"},
            new String[]{"Purchase", "NY"},
            new String[]{"London", "UK"},
            new String[]{"Charlotte", "NC"},
            new String[]{"McLean", "VA"},
            new String[]{"Miami", "FL"},
            new String[]{"Chicago", "IL"},
            new String[]{"Brooklyn", "NY"},
            new String[]{"Ottawa", "CA"},
            new String[]{"Boston", "MA"},
            new String[]{"Plantation", "FL"},
            new String[]{"Amsterdam", "NL"},
            new String[]{"Needham", "MA"},
            new String[]{"Singapore", "SG"},
            new String[]{"Jakarta", "ID"},
            new String[]{"Buenos Aires", "AR"},
            new String[]{"Sao Paulo", "BR"},
            new String[]{"Bogota", "CO"},
            new String[]{"Gurgaon", "IN"},
            new String[]{"Bangalore", "IN"},
            new String[]{"Noida", "IN"},
            new String[]{"Chennai", "IN"},
            new String[]{"Mumbai", "IN"},
            new String[]{"Teaneck", "NJ"},
            new String[]{"Dublin", "IE"},
            new String[]{"Paris", "FR"},
            new String[]{"Newtown", "PA"},
            new String[]{"Toronto", "CA"},
            new String[]{"Denver", "CO"},
            new String[]{"Irvine", "CA"},
            new String[]{"Newark", "CA"},
            new String[]{"Dearborn", "MI"},
            new String[]{"Detroit", "MI"},
            new String[]{"Toyota City", "JP"},
            new String[]{"Pittsburgh", "PA"},
            new String[]{"Hawthorne", "CA"},
            new String[]{"Kent", "WA"},
            new String[]{"Arlington", "VA"},
            new String[]{"Bethesda", "MD"},
            new String[]{"Falls Church", "VA"},
            new String[]{"Costa Mesa", "CA"},
            new String[]{"Munich", "DE"},
            new String[]{"Gerlingen", "DE"},
            new String[]{"Veldhoven", "NL"},
            new String[]{"Hsinchu", "TW"},
            new String[]{"Cambridge", "UK"},
            new String[]{"Fremont", "CA"},
            new String[]{"Bentonville", "AR"},
            new String[]{"Minneapolis", "MN"},
            new String[]{"Issaquah", "WA"},
            new String[]{"Atlanta", "GA"},
            new String[]{"Beaverton", "OR"},
            new String[]{"Cincinnati", "OH"},
            new String[]{"Burbank", "CA"},
            new String[]{"Philadelphia", "PA"},
            new String[]{"Minnetonka", "MN"},
            new String[]{"Woonsocket", "RI"},
            new String[]{"New Brunswick", "NJ"},
            new String[]{"Verona", "WI"},
            new String[]{"Kansas City", "MO"},
            new String[]{"Basel", "CH"},
            new String[]{"Prague", "CZ"},
            new String[]{"Tel Aviv", "IL"},
            new String[]{"Santa Barbara", "CA"},
            new String[]{"Olathe", "KS"},
            new String[]{"Seoul", "KR"},
            new String[]{"Moscow", "RU"},
            new String[]{"Seongnam", "KR"},
            new String[]{"Berlin", "DE"},
            new String[]{"Taipei", "TW"},
            new String[]{"New Taipei", "TW"},
            new String[]{"Lausanne", "CH"}
    );

    // Spec section 2.4 — per-company SWE ladder.
    private static final List<Object[]> LEVELS = List.of(
            // {companySlug, levelName, normalizedRank}
            new Object[]{"amazon", "SDE I", 1},
            new Object[]{"amazon", "SDE II", 2},
            new Object[]{"amazon", "SDE III", 3},
            new Object[]{"amazon", "Principal SDE", 4},
            new Object[]{"google", "L3", 1},
            new Object[]{"google", "L4", 2},
            new Object[]{"google", "L5", 3},
            new Object[]{"google", "L6", 4},
            new Object[]{"google", "L7", 5},
            new Object[]{"meta", "E3", 1},
            new Object[]{"meta", "E4", 2},
            new Object[]{"meta", "E5", 3},
            new Object[]{"meta", "E6", 4},
            new Object[]{"meta", "E7", 5},
            new Object[]{"microsoft", "SWE", 1},
            new Object[]{"microsoft", "SWE II", 2},
            new Object[]{"microsoft", "Senior SWE", 3},
            new Object[]{"microsoft", "Principal SWE", 4},

            // Apple — ICT track
            new Object[]{"apple", "ICT2", 1},
            new Object[]{"apple", "ICT3", 2},
            new Object[]{"apple", "ICT4", 3},
            new Object[]{"apple", "ICT5", 4},
            new Object[]{"apple", "ICT6", 5},

            // Netflix — flat ladder
            new Object[]{"netflix", "Senior Software Engineer", 1},
            new Object[]{"netflix", "Staff Software Engineer", 2},
            new Object[]{"netflix", "Principal Software Engineer", 3},

            // Stripe
            new Object[]{"stripe", "L1", 1},
            new Object[]{"stripe", "L2", 2},
            new Object[]{"stripe", "L3", 3},
            new Object[]{"stripe", "L4", 4},
            new Object[]{"stripe", "L5", 5},

            // Uber
            new Object[]{"uber", "L3", 1},
            new Object[]{"uber", "L4", 2},
            new Object[]{"uber", "L5", 3},
            new Object[]{"uber", "L5b", 4},
            new Object[]{"uber", "L6", 5},
            new Object[]{"uber", "L7", 6},

            // Airbnb — G track
            new Object[]{"airbnb", "G7", 1},
            new Object[]{"airbnb", "G8", 2},
            new Object[]{"airbnb", "G9", 3},
            new Object[]{"airbnb", "G10", 4},
            new Object[]{"airbnb", "G11", 5},

            // Salesforce — MTS track
            new Object[]{"salesforce", "AMTS", 1},
            new Object[]{"salesforce", "MTS", 2},
            new Object[]{"salesforce", "SMTS", 3},
            new Object[]{"salesforce", "Lead MTS", 4},
            new Object[]{"salesforce", "Principal MTS", 5},

            // LinkedIn (Microsoft-aligned)
            new Object[]{"linkedin", "Associate SWE", 1},
            new Object[]{"linkedin", "SWE", 2},
            new Object[]{"linkedin", "Senior SWE", 3},
            new Object[]{"linkedin", "Staff SWE", 4},
            new Object[]{"linkedin", "Senior Staff SWE", 5},
            new Object[]{"linkedin", "Distinguished Engineer", 6},

            // Nvidia — IC track
            new Object[]{"nvidia", "IC1", 1},
            new Object[]{"nvidia", "IC2", 2},
            new Object[]{"nvidia", "IC3", 3},
            new Object[]{"nvidia", "IC4", 4},
            new Object[]{"nvidia", "IC5", 5},
            new Object[]{"nvidia", "IC6", 6},

            // Intel — grade track
            new Object[]{"intel", "Grade 5", 1},
            new Object[]{"intel", "Grade 6", 2},
            new Object[]{"intel", "Grade 7", 3},
            new Object[]{"intel", "Grade 8", 4},
            new Object[]{"intel", "Principal Engineer", 5},

            // Oracle — IC track
            new Object[]{"oracle", "IC1", 1},
            new Object[]{"oracle", "IC2", 2},
            new Object[]{"oracle", "IC3", 3},
            new Object[]{"oracle", "IC4", 4},
            new Object[]{"oracle", "IC5", 5},

            // IBM — band track
            new Object[]{"ibm", "Band 6", 1},
            new Object[]{"ibm", "Band 7", 2},
            new Object[]{"ibm", "Band 8", 3},
            new Object[]{"ibm", "Band 9", 4},
            new Object[]{"ibm", "Band 10", 5},

            // Cisco — grade track
            new Object[]{"cisco", "Grade 8", 1},
            new Object[]{"cisco", "Grade 9", 2},
            new Object[]{"cisco", "Grade 10", 3},
            new Object[]{"cisco", "Grade 11", 4},
            new Object[]{"cisco", "Principal Engineer", 5},

            // Adobe — track
            new Object[]{"adobe", "Software Engineer 2", 1},
            new Object[]{"adobe", "Software Engineer 3", 2},
            new Object[]{"adobe", "Software Engineer 4", 3},
            new Object[]{"adobe", "Senior Software Engineer", 4},
            new Object[]{"adobe", "Principal Scientist", 5},

            // Tesla
            new Object[]{"tesla", "Associate Engineer", 1},
            new Object[]{"tesla", "Engineer", 2},
            new Object[]{"tesla", "Senior Engineer", 3},
            new Object[]{"tesla", "Staff Engineer", 4},
            new Object[]{"tesla", "Principal Engineer", 5},

            // Coinbase
            new Object[]{"coinbase", "IC3", 1},
            new Object[]{"coinbase", "IC4", 2},
            new Object[]{"coinbase", "IC5", 3},
            new Object[]{"coinbase", "IC6", 4},

            // Snap
            new Object[]{"snap", "L3", 1},
            new Object[]{"snap", "L4", 2},
            new Object[]{"snap", "L5", 3},
            new Object[]{"snap", "L6", 4},

            // Lyft
            new Object[]{"lyft", "T3", 1},
            new Object[]{"lyft", "T4", 2},
            new Object[]{"lyft", "T5", 3},
            new Object[]{"lyft", "T6", 4},

            // DoorDash
            new Object[]{"doordash", "E3", 1},
            new Object[]{"doordash", "E4", 2},
            new Object[]{"doordash", "E5", 3},
            new Object[]{"doordash", "E6", 4},

            // Robinhood
            new Object[]{"robinhood", "L3", 1},
            new Object[]{"robinhood", "L4", 2},
            new Object[]{"robinhood", "L5", 3},
            new Object[]{"robinhood", "L6", 4},

            // Dropbox
            new Object[]{"dropbox", "IC1", 1},
            new Object[]{"dropbox", "IC2", 2},
            new Object[]{"dropbox", "IC3", 3},
            new Object[]{"dropbox", "IC4", 4},
            new Object[]{"dropbox", "IC5", 5},

            // Instacart
            new Object[]{"instacart", "L3", 1},
            new Object[]{"instacart", "L4", 2},
            new Object[]{"instacart", "L5", 3},
            new Object[]{"instacart", "L6", 4},

            // PayPal
            new Object[]{"paypal", "T22", 1},
            new Object[]{"paypal", "T23", 2},
            new Object[]{"paypal", "T24", 3},
            new Object[]{"paypal", "T25", 4},

            // Atlassian
            new Object[]{"atlassian", "P3", 1},
            new Object[]{"atlassian", "P4", 2},
            new Object[]{"atlassian", "P5", 3},
            new Object[]{"atlassian", "P6", 4},

            // Snowflake
            new Object[]{"snowflake", "IC2", 1},
            new Object[]{"snowflake", "IC3", 2},
            new Object[]{"snowflake", "IC4", 3},
            new Object[]{"snowflake", "IC5", 4},

            // Databricks
            new Object[]{"databricks", "L3", 1},
            new Object[]{"databricks", "L4", 2},
            new Object[]{"databricks", "L5", 3},
            new Object[]{"databricks", "L6", 4},

            // Pinterest
            new Object[]{"pinterest", "L3", 1},
            new Object[]{"pinterest", "L4", 2},
            new Object[]{"pinterest", "L5", 3},
            new Object[]{"pinterest", "L6", 4},

            // Reddit
            new Object[]{"reddit", "IC3", 1},
            new Object[]{"reddit", "IC4", 2},
            new Object[]{"reddit", "IC5", 3},
            new Object[]{"reddit", "IC6", 4},

            // Spotify
            new Object[]{"spotify", "Engineer I", 1},
            new Object[]{"spotify", "Engineer II", 2},
            new Object[]{"spotify", "Senior Engineer", 3},
            new Object[]{"spotify", "Staff Engineer", 4},

            // Shopify
            new Object[]{"shopify", "Developer", 1},
            new Object[]{"shopify", "Senior Developer", 2},
            new Object[]{"shopify", "Staff Developer", 3},
            new Object[]{"shopify", "Principal Developer", 4},

            // Twilio
            new Object[]{"twilio", "TL2", 1},
            new Object[]{"twilio", "TL3", 2},
            new Object[]{"twilio", "TL4", 3},
            new Object[]{"twilio", "TL5", 4},

            // Roblox
            new Object[]{"roblox", "SWE I", 1},
            new Object[]{"roblox", "SWE II", 2},
            new Object[]{"roblox", "Senior SWE", 3},
            new Object[]{"roblox", "Principal SWE", 4},

            // OpenAI
            new Object[]{"openai", "IC3", 1},
            new Object[]{"openai", "IC4", 2},
            new Object[]{"openai", "IC5", 3},
            new Object[]{"openai", "IC6", 4},

            // Palantir
            new Object[]{"palantir", "Engineer 1", 1},
            new Object[]{"palantir", "Engineer 2", 2},
            new Object[]{"palantir", "Engineer 3", 3},
            new Object[]{"palantir", "Lead Engineer", 4},

            // Goldman Sachs
            new Object[]{"goldman-sachs", "Analyst", 1},
            new Object[]{"goldman-sachs", "Associate", 2},
            new Object[]{"goldman-sachs", "Vice President", 3},
            new Object[]{"goldman-sachs", "Managing Director", 4},

            // JPMorgan Chase
            new Object[]{"jpmorgan-chase", "Analyst", 1},
            new Object[]{"jpmorgan-chase", "Associate", 2},
            new Object[]{"jpmorgan-chase", "Vice President", 3},
            new Object[]{"jpmorgan-chase", "Executive Director", 4},

            // Capital One
            new Object[]{"capital-one", "Associate", 1},
            new Object[]{"capital-one", "Senior Associate", 2},
            new Object[]{"capital-one", "Principal Associate", 3},
            new Object[]{"capital-one", "Manager", 4}
    );

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        int companies = seedCompanies();
        int roles = seedRoles();
        int locations = seedLocations();
        int levels = seedLevels();
        int companyRoles = seedCompanyRoles();
        int experiences = seedExperiences();
        log.info("Lookup seed complete. inserted: companies={}, roles={}, locations={}, levels={}, company_roles={}, experiences={}",
                companies, roles, locations, levels, companyRoles, experiences);
    }

    private int seedCompanies() {
        int inserted = 0;
        for (String[] row : COMPANIES) {
            String slug = SlugUtil.slugify(row[0]);
            if (companyRepository.findBySlug(slug).isPresent()) continue;
            companyRepository.save(Company.builder()
                    .slug(slug)
                    .name(row[0])
                    .industry(row[1])
                    .headquarters(row[2])
                    .active(true)
                    .build());
            inserted++;
        }
        return inserted;
    }

    private int seedRoles() {
        int inserted = 0;
        for (String[] row : ROLES) {
            String slug = SlugUtil.slugify(row[0]);
            if (roleRepository.findBySlug(slug).isPresent()) continue;
            roleRepository.save(Role.builder()
                    .slug(slug)
                    .name(row[0])
                    .family(row[1])
                    .active(true)
                    .build());
            inserted++;
        }
        return inserted;
    }

    /**
     * Declare which roles are available at which companies.
     * - Base 4 roles are mapped to ALL companies
     * - "AI Engineer" only to Amazon
     * - "Network Engineer" only to Google
     */
    private int seedCompanyRoles() {
        List<Company> companies = companyRepository.findAll();
        if (companies.isEmpty()) return 0;

        int inserted = 0;
        // Map base roles to all companies
        for (String name : List.of("Software Engineer", "Senior Software Engineer", "Staff Engineer", "Engineering Manager")) {
            Role role = roleRepository.findBySlug(SlugUtil.slugify(name)).orElse(null);
            if (role == null) continue;
            for (Company c : companies) {
                if (companyRoleRepository.existsByCompany_IdAndRole_Id(c.getId(), role.getId())) continue;
                companyRoleRepository.save(CompanyRole.builder()
                        .company(c)
                        .role(role)
                        .active(true)
                        .build());
                inserted++;
            }
        }

        // AI Engineer only at Amazon
        Company amazon = companyRepository.findBySlug("amazon").orElse(null);
        Role aiEngineer = roleRepository.findBySlug("ai-engineer").orElse(null);
        if (amazon != null && aiEngineer != null &&
                !companyRoleRepository.existsByCompany_IdAndRole_Id(amazon.getId(), aiEngineer.getId())) {
            companyRoleRepository.save(CompanyRole.builder()
                    .company(amazon)
                    .role(aiEngineer)
                    .active(true)
                    .build());
            inserted++;
        }

        // Network Engineer only at Google
        Company google = companyRepository.findBySlug("google").orElse(null);
        Role networkEngineer = roleRepository.findBySlug("network-engineer").orElse(null);
        if (google != null && networkEngineer != null &&
                !companyRoleRepository.existsByCompany_IdAndRole_Id(google.getId(), networkEngineer.getId())) {
            companyRoleRepository.save(CompanyRole.builder()
                    .company(google)
                    .role(networkEngineer)
                    .active(true)
                    .build());
            inserted++;
        }

        return inserted;
    }

    private int seedLocations() {
        int inserted = 0;
        for (String[] row : LOCATIONS) {
            String city = row[0];
            String state = row[1];
            if (locationRepository.findByCityAndState(city, state).isPresent()) continue;
            locationRepository.save(Location.builder()
                    .slug(SlugUtil.slugify(city + " " + state))
                    .city(city)
                    .state(state)
                    .active(true)
                    .build());
            inserted++;
        }
        return inserted;
    }

    /**
     * Seeds a small set of published {@link Experience} rows for every active company so the
     * UI has aggregates (worth score, experience count, comp ranges) to render. Idempotent:
     * if a company already has any experiences, it is skipped.
     */
    private int seedExperiences() {
        List<Role> allRoles = roleRepository.findAll();
        List<Location> allLocations = locationRepository.findAll();
        if (allRoles.isEmpty() || allLocations.isEmpty()) {
            log.warn("Skipping experience seed — roles or locations not seeded yet.");
            return 0;
        }

        // Deterministic so re-runs of the seeder produce stable test data.
        Random rng = new Random(42);
        int inserted = 0;
        for (Company company : companyRepository.findAll()) {
            // Only the original demo companies receive sample experiences. The larger catalog of
            // companies is for typeahead/lookup only and must stay experience-free.
            if (!EXPERIENCE_SEED_SLUGS.contains(company.getSlug())) continue;

            // Idempotent guard: don't pile on more rows every boot.
            List<Level> companyLevels = levelRepository.findByCompany_IdOrderByNormalizedRankAsc(company.getId());
            long existing = experienceRepository.countByCompany_Id(company.getId());
            if (existing > 0) continue;

            for (int i = 0; i < EXPERIENCES_PER_COMPANY; i++) {
                Role role = allRoles.get(rng.nextInt(allRoles.size()));
                Location location = allLocations.get(rng.nextInt(allLocations.size()));
                Level level = companyLevels.isEmpty() ? null : companyLevels.get(rng.nextInt(companyLevels.size()));

                short years = (short) (1 + rng.nextInt(15));
                short yearsAtCo = (short) Math.min(years, 1 + rng.nextInt(8));
                int base = 120_000 + rng.nextInt(180_000);          // 120k–300k
                int bonus = rng.nextInt(60_000);                    // 0–60k
                int stock = rng.nextInt(250_000);                   // 0–250k
                int signing = rng.nextInt(50_000);                  // 0–50k
                short hours = (short) (35 + rng.nextInt(40));       // 35–75
                BigDecimal stress = BigDecimal.valueOf(1 + rng.nextInt(90), 1);    // 0.1–9.0
                BigDecimal worth = BigDecimal.valueOf(20 + rng.nextInt(81), 1)     // 2.0–10.0
                        .setScale(1, RoundingMode.HALF_UP);
                EmploymentStatus emp = rng.nextBoolean() ? EmploymentStatus.current : EmploymentStatus.past;

                experienceRepository.save(Experience.builder()
                        .company(company)
                        .role(role)
                        .location(location)
                        .level(level)
                        .levelName(level == null ? null : level.getName())
                        .employmentStatus(emp)
                        .yearsExperience(years)
                        .yearsAtCompany(yearsAtCo)
                        .baseSalary(base)
                        .bonus(bonus)
                        .stock(stock)
                        .signingBonus(signing)
                        .compensationYear((short) 2025)
                        .stressLevel(stress)
                        .hoursPerWeek(hours)
                        .worthItScore(worth)
                        .wishKnew("Seeded test data for " + company.getName() + ".")
                        .status(ExperienceStatus.published)
                        .build());
                inserted++;
            }
        }
        return inserted;
    }

    private int seedLevels() {
        int inserted = 0;
        for (Object[] row : LEVELS) {
            String companySlug = (String) row[0];
            String levelName = (String) row[1];
            int rank = (int) row[2];
            Company company = companyRepository.findBySlug(companySlug).orElse(null);
            if (company == null) continue;
            if (levelRepository.findByCompany_IdAndName(company.getId(), levelName).isPresent()) continue;
            levelRepository.save(Level.builder()
                    .company(company)
                    .name(levelName)
                    .normalizedRank(rank)
                    .active(true)
                    .build());
            inserted++;
        }
        return inserted;
    }
}
