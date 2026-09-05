"""Real Karnataka district/village/name data the generators draw from.
Everything downstream of seed generation traces back to this file."""

DISTRICT_VILLAGES = {
    "Bengaluru Rural": ["Devanahalli", "Doddaballapura", "Hoskote", "Nelamangala"],
    "Tumakuru": ["Tumakuru", "Sira", "Madhugiri", "Koratagere"],
    "Ramanagara": ["Ramanagara", "Channapatna", "Kanakapura", "Magadi"],
    "Kolar": ["Kolar", "Malur", "Mulbagal", "Bangarpet"],
}

DISTRICT_ABBR = {
    "Bengaluru Rural": "BRU",
    "Tumakuru": "TUM",
    "Ramanagara": "RMN",
    "Kolar": "KLR",
}

FIRST_NAMES_MALE = [
    "Manjunath", "Siddaraju", "Basavaraj", "Ramesh", "Suresh", "Nagaraj",
    "Krishnappa", "Puttaswamy", "Venkatesh", "Chandrashekar", "Gopal",
    "Muniraju", "Lakshman", "Shivakumar", "Rangaswamy", "Anand", "Prakash",
]

FIRST_NAMES_FEMALE = [
    "Lakshmamma", "Gowramma", "Nagarathna", "Manjula", "Savitri", "Kaveri",
    "Rathnamma", "Shobha", "Vijayalakshmi", "Puttamma", "Girija", "Yashoda",
    "Nirmala", "Sharada", "Jayamma", "Roopa", "Kavya",
]

LAST_NAMES = [
    "Gowda", "Naik", "Reddy", "Shetty", "Rao", "Murthy", "Iyengar",
    "Nayaka", "Hegde", "Setty",
]

# Which document types each stage requires. Owned by us for now — see the
# note on db.models.RequiredDocument.
REQUIRED_DOCUMENTS = {
    "preliminary_notification": ["notification_gazette"],
    "social_impact_assessment": ["sia_report"],
    "verification": ["survey_report", "ownership_record"],
    "objection_period": ["objection_register"],
    "declaration": ["declaration_gazette"],
    "award": ["award_order"],
    "rnr": ["rnr_entitlement_list"],
    "possession": ["possession_certificate"],
    "monitoring": [],
}

OBJECTION_GROUNDS = [
    "Compensation amount is inadequate",
    "Survey boundary is incorrect",
    "Livelihood loss not accounted for",
    "Ancestral property, ownership disputed",
    "Alternate land not offered",
    "Notice was not properly served",
    "Crop and tree valuation understated",
]

# 8 projects, each pinned to one of the four districts above.
PROJECTS = [
    {"name": "Bengaluru–Tumakuru Industrial Corridor Highway", "requiring_body": "National Highways Authority of India", "district_name": "Bengaluru Rural"},
    {"name": "Devanahalli Airport Expansion Link Road", "requiring_body": "Airports Authority of India", "district_name": "Bengaluru Rural"},
    {"name": "Doddaballapura Water Reservoir Project", "requiring_body": "Karnataka Neeravari Nigam", "district_name": "Bengaluru Rural"},
    {"name": "Tumakuru–Sira Rail Freight Corridor", "requiring_body": "South Western Railway", "district_name": "Tumakuru"},
    {"name": "Madhugiri Irrigation Canal Extension", "requiring_body": "Karnataka Neeravari Nigam", "district_name": "Tumakuru"},
    {"name": "Channapatna Bypass Road", "requiring_body": "Karnataka Public Works Department", "district_name": "Ramanagara"},
    {"name": "Kanakapura Industrial Park Access Road", "requiring_body": "Karnataka Industrial Area Development Board", "district_name": "Ramanagara"},
    {"name": "Kolar–Malur Power Transmission Corridor", "requiring_body": "Karnataka Power Transmission Corporation", "district_name": "Kolar"},
]
