package org.briarproject.briar.android.sushh.broadcast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public final class LocationScope {

	private final String country;
	private final String state;
	private final String district;
	private final String zone;

	public LocationScope(String country, String state, String district,
			String zone) {
		this.country = normalize(country);
		this.state = normalize(state);
		this.district = normalize(district);
		this.zone = normalize(zone);
	}

	public static LocationScope global() {
		return new LocationScope("", "", "", "");
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	public boolean includes(LocationScope participantLocation) {
		return matches(country, participantLocation.country) &&
				matches(state, participantLocation.state) &&
				matches(district, participantLocation.district) &&
				matches(zone, participantLocation.zone);
	}

	private static boolean matches(String target, String actual) {
		return target.isEmpty() || target.equalsIgnoreCase(actual);
	}

	void writeCanonical(DataOutputStream out) throws IOException {
		out.writeUTF(country);
		out.writeUTF(state);
		out.writeUTF(district);
		out.writeUTF(zone);
	}

	public String getCountry() {
		return country;
	}

	public String getState() {
		return state;
	}

	public String getDistrict() {
		return district;
	}

	public String getZone() {
		return zone;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (!(object instanceof LocationScope)) return false;
		LocationScope that = (LocationScope) object;
		return country.equals(that.country) && state.equals(that.state) &&
				district.equals(that.district) && zone.equals(that.zone);
	}

	@Override
	public int hashCode() {
		return Objects.hash(country, state, district, zone);
	}
}
