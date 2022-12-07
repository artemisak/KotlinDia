package com.almazov.diacompanion.record_info

import FoodInMealInfoAdapter
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.transition.TransitionInflater
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.almazov.diacompanion.R
import com.almazov.diacompanion.base.*
import com.almazov.diacompanion.data.AppDatabaseViewModel
import com.almazov.diacompanion.data.MealWithFood
import com.almazov.diacompanion.meal.FoodInMealItem
import com.almazov.diacompanion.meal.FoodInMealListAdapter
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.android.synthetic.main.fragment_meal_record_info.*
import kotlinx.android.synthetic.main.fragment_meal_record_info.recycler_view_food_in_meal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.math.BigDecimal

class MealRecordInfo : Fragment(), FoodInMealListAdapter.InterfaceFoodInMeal {
    private val args by navArgs<MealRecordInfoArgs>()
    private lateinit var appDatabaseViewModel: AppDatabaseViewModel
    var mealInfo = listOf<Double>()
    var foodList = mutableListOf<FoodInMealItem>()
    lateinit var adapter: FoodInMealListAdapter
    private var bmi: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val animation = TransitionInflater.from(requireContext()).inflateTransition(
            android.R.transition.move
        )
        sharedElementEnterTransition = animation
        sharedElementReturnTransition = animation
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        bmi = sharedPreferences!!.getFloat("BMI", 20f).toDouble()

        return inflater.inflate(R.layout.fragment_meal_record_info, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        appDatabaseViewModel = ViewModelProvider(this)[AppDatabaseViewModel::class.java]

        appDatabaseViewModel.getMealWithFoods(args.selectedRecord.id).observe(viewLifecycleOwner, Observer{record ->
            if (!record.isNullOrEmpty()) {
                for (food in record) {
                    foodList.add(FoodInMealItem(food.food, food.weight!!))
                    adapter.notifyItemInserted(foodList.size)
                }

                setPieChart()
                tv_kkal.text = mealInfo[3].toInt().toString() + " ККал"
                tv_gi.text = mealInfo[4].toInt().toString()
                tv_gl.text = mealInfo[5].toInt().toString()
                tv_weight.text = mealInfo[6].toInt().toString() + " гр."

                if (record[0].meal.sugarLevel != null) {
                    tv_sugar_level_before.text = record[0].meal.sugarLevel.toString()
                    tv_sugar_level_predict.text = record[0].meal.sugarLevelPredicted.toString()
                }
            }
        })

        adapter = FoodInMealInfoAdapter(foodList, this)
        recycler_view_food_in_meal.adapter = adapter
        recycler_view_food_in_meal.layoutManager = LinearLayoutManager(requireContext())

        date.text = args.selectedRecord.date
        time.text = args.selectedRecord.time

        btn_edit.setOnClickListener{
            val action = MealRecordInfoDirections.actionMealRecordInfoToMealAddRecord(args.selectedRecord)
            findNavController().navigate(action)
        }

        btn_delete.setOnClickListener{
            val builder = AlertDialog.Builder(requireContext())
            builder.setPositiveButton(this.resources.getString(R.string.Yes)) {_, _ ->
                appDatabaseViewModel.deleteMealRecord(args.selectedRecord?.id)
                args.selectedRecord.let { appDatabaseViewModel.deleteRecord(it) }
                findNavController().popBackStack()
            }
            builder.setNegativeButton(this.resources.getString(R.string.No)) {_, _ ->
            }
            builder.setTitle(this.resources.getString(R.string.DeleteRecord))
            builder.setMessage(this.resources.getString(R.string.AreUSureDeleteRecord))
            builder.create().show()
        }

        super.onViewCreated(view, savedInstanceState)
    }

    /*private fun displayPredictedSugarLevelAndRecommendation(record: List<MealWithFood>) {
        tv_sugar_level_before.text = record[0].meal.sugarLevel.toString()

        val time = args.selectedRecord.time
        val date = args.selectedRecord.date
        val timeInMilli = convertDateToMils("$time $date")

        appDatabaseViewModel.getMealWithFoods6HoursAgo(timeInMilli)
            .observe(viewLifecycleOwner, Observer { proteinRecord ->
                val protein = getProtein(proteinRecord)
                GlobalScope.launch(Dispatchers.Main) {
                    val predict = GlobalScope.async(Dispatchers.Default) {
                        val glCarbsKr = getGLCarbsKr(foodList)
                        return@async predictSL(
                            requireContext(), record[0].meal.sugarLevel,
                            glCarbsKr, protein, record[0].meal.type, bmi
                        )
                    }
                    val highGi = GlobalScope.async((Dispatchers.Default)) {
//                        checkGI(record)
                    }
//                    val recommendation = getMessage(predict.await())
                    tv_sugar_level_predict.text = setTwoDigits(predict.await()).toString()

                }
            })
    }*/

    private fun setPieChart() {
        mealInfo = getMealInfo(foodList)
        tv_protein.text = mealInfo[0].toInt().toString() + " гр."
        tv_fats.text = mealInfo[1].toInt().toString() + " гр."
        tv_carbs.text = mealInfo[2].toInt().toString() + " гр."
        val names = listOf("Белки", "Жиры", "Углеводы")
        val pieEntries = ArrayList<PieEntry>()
        for (i in 0..2)
            pieEntries.add(PieEntry(mealInfo[i].toFloat(),names[i]))
        val colorsIds = listOf(R.color.green, R.color.yellow, R.color.orange)
        val darkColorsIds = listOf(R.color.green_dark, R.color.yellow_dark, R.color.orange_dark)
        val pieColors = ArrayList<Int>()
        val darkPieColors = ArrayList<Int>()
        for (i in 0 until colorsIds.count()) {
            pieColors.add(ContextCompat.getColor(requireContext(), colorsIds[i]))
            darkPieColors.add(ContextCompat.getColor(requireContext(), darkColorsIds[i]))
        }
        val pieDataset = PieDataSet(pieEntries,"")
        pieDataset.colors = pieColors
        val pieData = PieData(pieDataset)
        pieData.setDrawValues(true)
        pieData.setValueFormatter(PercentFormatter(meal_pie_chart))
        pieData.setValueTextSize(18f)

        val textColor = ContextCompat.getColor(requireContext(), R.color.purple_dark)
        val backgroundColor = ContextCompat.getColor(requireContext(), R.color.purple)
        pieData.setValueTextColors(darkPieColors)

        meal_pie_chart.apply{
            isDrawHoleEnabled = true
            setUsePercentValues(true)
            centerText = args.selectedRecord.mainInfo
            setCenterTextSize(22f)
            setCenterTextColor(textColor)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            isHighlightPerTapEnabled = false
            setHoleColor(backgroundColor)
            data = pieData
            animateY(1200, Easing.EaseInOutQuad)
            invalidate()
        }


    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val contextThemeWrapper: Context = ContextThemeWrapper(requireContext(), R.style.MealTheme)
        return inflater.cloneInContext(contextThemeWrapper)
    }

    override fun updateRecommendationWeight(position: Int, weight: Double) {

    }

}