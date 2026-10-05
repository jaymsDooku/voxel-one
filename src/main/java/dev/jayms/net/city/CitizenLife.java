package dev.jayms.net.city;

/** Saved individual life history. A year is twelve city days, independent of clock speed. */
public final class CitizenLife {
    public static final double DAYS_PER_YEAR = 12;
    public enum Gender { FEMALE, MALE }
    public enum Education { NONE, PRIMARY, SECONDARY, TECHNICAL, UNIVERSITY }
    public double age, study, lastBirthAge = -10;
    public Gender gender;
    public Education education;
    public int spouse, mother, father, school;

    public CitizenLife(double age, Gender gender, Education education) {
        this.age = age;
        this.gender = gender;
        this.education = education;
    }

    public static CitizenLife founder(int index) {
        return new CitizenLife(24 + index % 8, index % 2 == 0 ? Gender.FEMALE : Gender.MALE,
                index / 4 == 0 ? Education.NONE : index / 4 == 1 ? Education.TECHNICAL : Education.UNIVERSITY);
    }

    public boolean adult() { return age >= 18; }
    public String career() {
        return career(education);
    }
    public static String career(Education education) {
        return switch (education) {
            case NONE, PRIMARY -> "Manual work";
            case SECONDARY -> "Commercial / manual work";
            case TECHNICAL -> "Factory / commercial / manual work";
            case UNIVERSITY -> "Office / commercial / manual work";
        };
    }
}
