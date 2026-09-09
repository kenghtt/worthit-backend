package com.worthit.backend.dto;

/** A distinct city/state pair available to the experiences-table filter. */
public record ExperienceLocationOption(String city, String state) {
}
