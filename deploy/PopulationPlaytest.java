package dev.jayms;

import static org.lwjgl.opengl.GL33.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import java.nio.file.*;

/** Runs production simulation and native HUD with synthetic residents; no account data. */
public final class PopulationPlaytest {
    static void capture(Overlay ui, CitySimulation city, int citizen, Path file, String step) throws Exception {
        glViewport(0, 0, 1280, 720);
        glClearColor(.04f, .06f, .08f, 1); glClear(GL_COLOR_BUFFER_BIT);
        ui.begin(1280,720);
        var tools = new CityTools(); tools.selectedCitizen = citizen;
        tools.render(ui,1280,720,new Matrix4f(),new Matrix4f(),city.frame(),true);
        ui.text(step,24,15,1.35f);
        ui.text("Native city simulation | synthetic residents | Mesa OpenGL",24,43,1.1f);
        if (city.frame().buildings().stream().anyMatch(b -> b.id() == 10 && SpecialBuildings.special(b.type()))) {
            var info = new BuildingInfo(); info.show(10,0);
            float y = 165;
            for (String line : info.lines(city.frame())) { ui.text(line,24,y,1.2f); y += 27; }
        } else {
            float y = 170;
            for (var c : city.frame().citizens()) {
                ui.text(c.name() + " | Age " + (int)c.age() + " | " + c.gender()
                        + " | spouse #" + c.spouse() + " | home #" + c.home(),24,y,1.15f);
                y += 30;
            }
        }
        ui.end(); SpecialBuildingsSmoke.capture(file);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
    }
    public static void main(String[] args) throws Exception {
        GL.createCapabilities(); Path out = Path.of(args[0]); Files.createDirectories(out);
        var tests = new CitizenLifeTest();
        try (var ui = new Overlay()) {
            var college = tests.school(5,21,CitizenLife.Education.SECONDARY,2.99);
            college.advance(.11);
            if (college.life(1).school != 10 || college.life(1).study <= 2.99)
                throw new AssertionError("Attendance did not progress");
            capture(ui,college,1,out.resolve("population-technical-attendance.png"),"Playtest: age 21 attends technical college; study credit rises only in class");
            for (int i = 0; i < 30 && college.life(1).education != CitizenLife.Education.TECHNICAL; i++) college.advance(.11);
            if (college.life(1).education != CitizenLife.Education.TECHNICAL) throw new AssertionError("Technical graduation failed");
            capture(ui,college,1,out.resolve("population-technical-graduate.png"),"Playtest: technical graduate; factory career unlocked");
            var family = tests.family(19); family.advance(.11); family.advance(.11);
            if (family.frame().citizens().size() != 3) throw new AssertionError("Expected one child");
            capture(ui,family,family.frame().citizens().get(2).id(),out.resolve("population-family.png"),"Playtest: adults marry; newborn has both parents, shared home, and no job");
            var edge = tests.school(3,22,CitizenLife.Education.SECONDARY,0); edge.advance(.11);
            if (edge.life(1).school != 0 || edge.life(1).study != 0) throw new AssertionError("Age 22 enrolled");
            capture(ui,edge,1,out.resolve("population-age-edge.png"),"Playtest edge: age 22 cannot enrol in university; no free qualification");
            var university = tests.school(3,21,CitizenLife.Education.SECONDARY,2.9999); university.advance(.11);
            if (university.life(1).education != CitizenLife.Education.UNIVERSITY) throw new AssertionError("University graduation failed");
            capture(ui,university,1,out.resolve("population-university.png"),"Playtest: university graduate enters the office labour pool");
            tests.physicalAttendanceCompletesAllFourSchoolPaths();
            tests.agePrerequisitesCapacityAndNoRoutePreventFreeEducation();
            tests.adultsMarryHaveDependentChildrenAndCannotRepeatBirthEachTick();
            tests.legacySnapshotsMigrateWithoutChangingTheirLayout();
            tests.technicalCollegePermitUsesNewIdsAndInspectorShowsStudents();
            tests.educationGatesActualAssignmentAndEveryCareerType();
            tests.familyEdgesRequireSpaceAndRejectCloseRelatives();
            tests.agingUsesCityDaysAndInvalidFamilyReferencesAreRejected();
            var tools = new CityTools(); tools.tool = 6;
            var commands = new java.util.ArrayList<CityCommand>();
            tools.click(40,290,1280,720,new Matrix4f(),new Matrix4f(),college.frame(),commands::add);
            if (tools.specialKind != 5 || !commands.isEmpty()) throw new AssertionError("Technical menu click failed");
            glClear(GL_COLOR_BUFFER_BIT); ui.begin(1280,720);
            tools.render(ui,1280,720,new Matrix4f(),new Matrix4f(),college.frame(),true);
            ui.text("Playtest: select Technical college in the Special permit menu",24,15,1.35f);
            ui.end(); SpecialBuildingsSmoke.capture(out.resolve("population-college-permit.png"));
            // Exercise the ground placement click through the same permit UI used by Main.
            var terrain = new dev.jayms.net.Terrain(dev.jayms.net.Terrain.DEFAULT_SEED);
            int grade = terrain.column(8,24).height();
            var ground = new SpecialBuildingsTest.Ground(grade);
            var roads = new java.util.ArrayList<CityFrame.Road>();
            for (int x = 48; x < 64; x++) roads.add(new CityFrame.Road(x,50,grade));
            var config = GameConfig.cityGame();
            var site = new CitySimulation(config,ground,terrain,new CityFrame(config,0,roads,
                    java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of()));
            var projection = new Matrix4f().ortho(-24,12,-10.125f,10.125f,.1f,300);
            var view = new Matrix4f().lookAt(new org.joml.Vector3f(80,grade+35,85),
                    new org.joml.Vector3f(53,grade+3,54),new org.joml.Vector3f(0,1,0));
            var p = new Matrix4f(projection).mul(view).transform(new org.joml.Vector4f(50,grade+1.03f,52,1));
            tools.click((p.x/p.w*.5f+.5f)*1280,(.5f-p.y/p.w*.5f)*720,1280,720,
                    projection,view,site.frame(),command -> tools.message = site.command(command,1,null));
            if (!tools.message.equals("Permitted Technical college level 1") || ground.batches != 1)
                throw new AssertionError("Technical permit placement failed: " + tools.message);
            var bytes = new java.io.ByteArrayOutputStream(); site.frame().write(new java.io.DataOutputStream(bytes));
            var loaded = CityFrame.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
            if (!site.frame().buildings().equals(loaded.buildings())) throw new AssertionError("College save lost building");
        }
        System.out.println("PASS: population native workflow, age edge, school paths, family, careers and legacy reload");
    }
}
